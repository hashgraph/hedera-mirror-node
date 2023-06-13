/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.importer.migration;

import com.google.common.base.Stopwatch;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.MirrorProperties;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.inject.Named;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Named
public class SyntheticCryptoTransferApprovalMigration extends RepeatableMigration {

    private static final Long LOWER_BOUND_TIMESTAMP = 1680284879342064922L;
    private static final Long UPPER_BOUND_TIMESTAMP = 1686243920981874003L;

    Map<String, Long> MIGRATION_BOUNDARY_TIMESTAMPS = Map.of(
            "lower_bound", LOWER_BOUND_TIMESTAMP,
            "upper_bound", UPPER_BOUND_TIMESTAMP);

    private static final String TRANSFER_SQL =
            """
            with cryptotransfers as (
              select is_approval,
                ct.consensus_timestamp,
                ct.amount,
                cast(null as int) as index,
                cast(null as bigint) as receiver,
                entity_id as sender,
                cr.sender_id,
                cast(null as bigint) as token_id,
                'CRYPTO_TRANSFER' as transfer_type
              from crypto_transfer ct
              join contract_result cr on cr.consensus_timestamp = ct.consensus_timestamp
              where cr.consensus_timestamp > :lower_bound and
              cr.consensus_timestamp < :upper_bound and
              cr.sender_id <> entity_id and
              is_approval = false and
              ct.amount < 0
            ), tokentransfers as (
              select is_approval,
                t.consensus_timestamp,
                t.amount,
                cast(null as int) as index,
                cast(null as bigint) as receiver,
                account_id as sender,
                cr.sender_id,
                token_id,
                'TOKEN_TRANSFER' as transfer_type
              from token_transfer t
              join contract_result cr on cr.consensus_timestamp = t.consensus_timestamp
              where cr.consensus_timestamp > :lower_bound and
              cr.consensus_timestamp < :upper_bound and
              cr.sender_id <> account_id and
              is_approval = false and
              t.amount < 0
            ), nfttransfers as (
              select (arr.item->>'is_approval')::boolean as is_approval,
                t.consensus_timestamp,
                cast(null as bigint) as amount,
                arr.index,
                (arr.item->>'receiver_account_id')::bigint as receiver,
                (arr.item->>'sender_account_id')::bigint as sender,
                cr.sender_id,
                cast(null as bigint) as token_id,
                'NFT_TRANSFER' as transfer_type
              from transaction t
              join contract_result cr on cr.consensus_timestamp = t.consensus_timestamp,
              jsonb_array_elements(nft_transfer) with ordinality arr(item, index)
              where cr.consensus_timestamp > :lower_bound and
              cr.consensus_timestamp < :upper_bound and
              cr.sender_id <> (arr.item->>'sender_account_id')::bigint and
              (arr.item->>'is_approval')::boolean = false
            ), entity as (
                select key, id, timestamp_range from entity
                union all
                select key, id, timestamp_range from entity_history
            ), cryptoentities as (
              select ct.*, e.key
              from cryptotransfers ct
              join entity e on e.timestamp_range @> ct.consensus_timestamp::bigint
              where e.id = ct.sender
            ), tokenentities as (
              select t.*, e.key
              from tokentransfers t
              join entity e on e.timestamp_range @> t.consensus_timestamp::bigint
              where e.id = t.sender
            ), nftentities as (
              select nft.*, e.key
              from nfttransfers nft
              join entity e on e.timestamp_range @> nft.consensus_timestamp::bigint
              where e.id = nft.receiver
            ) select * from cryptoentities
              union all
              select * from tokenentities
              union all
              select * from nftentities
              order by consensus_timestamp asc
            """;

    private static final String UPDATE_CRYPTO_TRANSFER_SQL =
            """
            update crypto_transfer set is_approval = true where amount = :amount and consensus_timestamp = :consensus_timestamp and entity_id = :sender
            """;
    private static final String UPDATE_NFT_TRANSFER_SQL =
            """
            update transaction set nft_transfer = jsonb_set(nft_transfer, array[:index::text, 'is_approval'], 'true', false) where consensus_timestamp = :consensus_timestamp
            """;
    private static final String UPDATE_TOKEN_TRANSFER_SQL =
            """
            update token_transfer set is_approval = true where consensus_timestamp = :consensus_timestamp and token_id = :token_id and account_id = :sender
            """;

    public enum TRANSFER_TYPE {
        CRYPTO_TRANSFER,
        NFT_TRANSFER,
        TOKEN_TRANSFER
    }

    private final MirrorProperties mirrorProperties;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final TransferMapper transferMapper = new TransferMapper();

    @Lazy
    public SyntheticCryptoTransferApprovalMigration(
            NamedParameterJdbcTemplate jdbcTemplate,
            MirrorProperties mirrorProperties,
            TransactionTemplate transactionTemplate) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
        this.mirrorProperties = mirrorProperties;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public String getDescription() {
        return "Update the is_approval value for synthetic crypto transfers";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version where the nft_transfer table was migrated to the transaction table
        return MigrationVersion.fromVersion("1.81.0");
    }

    @Override
    protected void doMigrate() {
        if (!isMainnet()) {
            return;
        }

        AtomicLong count = new AtomicLong(0L);
        var migrationErrors = new ArrayList<String>();
        var stopwatch = Stopwatch.createStarted();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                var transfers = jdbcTemplate.query(TRANSFER_SQL, MIGRATION_BOUNDARY_TIMESTAMPS, transferMapper);
                for (ApprovalTransfer transfer : transfers) {
                    if (!isAuthorizedByContractKey(transfer, migrationErrors)) {
                        // set is_approval to true
                        switch (transfer.transferType) {
                            case CRYPTO_TRANSFER:
                                var updateMap = Map.of(
                                        "amount",
                                        transfer.amount,
                                        "consensus_timestamp",
                                        transfer.consensusTimestamp,
                                        "sender",
                                        transfer.sender);
                                jdbcTemplate.update(UPDATE_CRYPTO_TRANSFER_SQL, updateMap);
                                break;
                            case NFT_TRANSFER:
                                var nftUpdateMap = Map.of(
                                        "index", transfer.index, "consensus_timestamp", transfer.consensusTimestamp);
                                jdbcTemplate.update(UPDATE_NFT_TRANSFER_SQL, nftUpdateMap);
                                break;
                            case TOKEN_TRANSFER:
                                var tokenUpdateMap = Map.of(
                                        "consensus_timestamp",
                                        transfer.consensusTimestamp,
                                        "token_id",
                                        transfer.tokenId,
                                        "sender",
                                        transfer.sender);
                                jdbcTemplate.update(UPDATE_TOKEN_TRANSFER_SQL, tokenUpdateMap);
                        }
                        count.incrementAndGet();
                    }
                }
            });
        } catch (Exception e) {
            log.error("Error migrating transfer approvals", e);
            return;
        }

        log.info("Updated {} crypto transfer approvals in {}", count, stopwatch);
        migrationErrors.forEach(log::error);
    }

    /**
     * A return value of false denotes that the transfer's isApproval value should be set to true
     */
    private boolean isAuthorizedByContractKey(ApprovalTransfer transfer, List<String> migrationErrors) {
        if (transfer.key == null) {
            return false;
        }

        Key parsedKey;
        try {
            parsedKey = Key.parseFrom(transfer.key);
        } catch (Exception e) {
            migrationErrors.add(String.format(
                    "Unable to determine if transfer should be migrated. Entity id %d at %d: %s",
                    transfer.sender, transfer.consensusTimestamp, e.getMessage()));
            return true;
        }

        // If the threshold is greater than one ignore it
        if (!parsedKey.hasThresholdKey() || parsedKey.getThresholdKey().getThreshold() > 1) {
            return false;
        }

        return isAuthorizedByThresholdKey(parsedKey, transfer.senderId);
    }

    private boolean isAuthorizedByThresholdKey(Key parsedKey, long senderId) {
        var keys = parsedKey.getThresholdKey().getKeys().getKeysList();
        for (var key : keys) {
            if (key.hasContractID() && EntityId.of(key.getContractID()).getId() == senderId) {
                return true;
            }
        }
        return false;
    }

    private boolean isMainnet() {
        return MirrorProperties.HederaNetwork.MAINNET.equalsIgnoreCase(mirrorProperties.getNetwork());
    }

    public class TransferMapper implements RowMapper<ApprovalTransfer> {
        @SneakyThrows
        public ApprovalTransfer mapRow(ResultSet rs, int rowNum) {
            var migrationTransfer = new ApprovalTransfer();
            migrationTransfer.amount = rs.getLong("amount");
            migrationTransfer.consensusTimestamp = rs.getLong("consensus_timestamp");
            migrationTransfer.index = rs.getInt("index") - 1;
            migrationTransfer.key = rs.getBytes("key");
            migrationTransfer.sender = rs.getLong("sender");
            migrationTransfer.senderId = rs.getLong("sender_id");
            migrationTransfer.tokenId = rs.getLong("token_id");
            migrationTransfer.transferType = TRANSFER_TYPE.valueOf(rs.getString("transfer_type"));
            return migrationTransfer;
        }
    }

    @NoArgsConstructor
    public class ApprovalTransfer {
        private Long amount;
        private Long consensusTimestamp;
        private Integer index;
        private byte[] key;
        private Long sender;
        private Long senderId;
        private Long tokenId;
        private TRANSFER_TYPE transferType;
    }
}
