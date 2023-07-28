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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.db.DBProperties;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.inject.Named;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.Getter;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

@Named
public class SyntheticCryptoTransferApprovalMigration extends AsyncJavaMigration<Long> {

    // The contract id of the first synthetic transfer that could have exhibited this problem
    private static final long GRANDFATHERED_ID = 2119900L;
    // The created timestamp of the grandfathered id contract
    static final long LOWER_BOUND_TIMESTAMP = 1680284879342064922L;
    // This problem was fixed by services release 0.38.10, this is last timestamp before that release
    static final long UPPER_BOUND_TIMESTAMP = 1686243920981874002L;
    private static final long TIMESTAMP_INCREMENT =
            Duration.ofDays(1).toNanos(); // 1 day in nanoseconds which will yield 69 async iterations
    private static final String TRANSFER_SQL =
            """
            with contractresults as (
              select
                consensus_timestamp,
                contract_id
                from contract_result
                where
                  consensus_timestamp > :lower_bound and
                  consensus_timestamp <= :upper_bound and
                  contract_id >= :grandfathered_id
            ), cryptotransfers as (
              select
                ct.consensus_timestamp,
                cast(null as int) as index,
                entity_id as sender,
                cr.contract_id,
                cast(null as bigint) as token_id,
                'CRYPTO_TRANSFER' as transfer_type
              from crypto_transfer ct
              join contractresults cr on cr.consensus_timestamp = ct.consensus_timestamp
              where
                cr.contract_id <> ct.entity_id and
                ct.is_approval = false and
                ct.amount < 0
            ), tokentransfers as (
              select
                t.consensus_timestamp,
                cast(null as int) as index,
                account_id as sender,
                cr.contract_id,
                token_id,
                'TOKEN_TRANSFER' as transfer_type
              from token_transfer t
              join contractresults cr on cr.consensus_timestamp = t.consensus_timestamp
              where
                cr.contract_id <> account_id and
                is_approval = false and
                t.amount < 0
            ), nfttransfers as (
              select
                t.consensus_timestamp,
                arr.index - 1 as index,
                (arr.item->>'sender_account_id')::bigint as sender,
                cr.contract_id,
                cast(null as bigint) as token_id,
                'NFT_TRANSFER' as transfer_type
              from transaction t
              join contractresults cr on cr.consensus_timestamp = t.consensus_timestamp,
              jsonb_array_elements(nft_transfer) with ordinality arr(item, index)
              where
                cr.contract_id <> (arr.item->>'sender_account_id')::bigint and
                (arr.item->>'is_approval')::boolean = false
            ), entity as (
                select key, id, timestamp_range from entity
                where key is not null
                union all
                select key, id, timestamp_range from entity_history
                where key is not null
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
              where e.id = nft.sender
            ) select * from cryptoentities
              union all
              select * from tokenentities
              union all
              select * from nftentities
              order by consensus_timestamp asc
            """;

    private static final String UPDATE_CRYPTO_TRANSFER_SQL =
            """
            update crypto_transfer set is_approval = true where consensus_timestamp = :consensus_timestamp and entity_id = :sender
            """;
    private static final String UPDATE_NFT_TRANSFER_SQL =
            """
            update transaction set nft_transfer = jsonb_set(nft_transfer, array[:index::text, 'is_approval'], 'true', false) where consensus_timestamp = :consensus_timestamp
            """;
    private static final String UPDATE_TOKEN_TRANSFER_SQL =
            """
            update token_transfer set is_approval = true where consensus_timestamp = :consensus_timestamp and token_id = :token_id and account_id = :sender
            """;

    private enum TRANSFER_TYPE {
        CRYPTO_TRANSFER,
        NFT_TRANSFER,
        TOKEN_TRANSFER
    }

    private static final DataClassRowMapper<SyntheticCryptoTransferApprovalMigration.ApprovalTransfer> resultRowMapper =
            new DataClassRowMapper<>(ApprovalTransfer.class);

    private final MirrorProperties mirrorProperties;
    private final NamedParameterJdbcTemplate transferJdbcTemplate;

    @Getter
    private final TransactionOperations transactionOperations;

    @Lazy
    public SyntheticCryptoTransferApprovalMigration(
            DBProperties dbProperties,
            MirrorProperties mirrorProperties,
            NamedParameterJdbcTemplate transferJdbcTemplate,
            TransactionOperations transactionOperations) {
        super(mirrorProperties.getMigration(), transferJdbcTemplate, dbProperties.getSchema());
        this.mirrorProperties = mirrorProperties;
        this.transferJdbcTemplate = transferJdbcTemplate;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public String getDescription() {
        return "Update the is_approval value for synthetic transfers";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version where the nft_transfer table was migrated to the transaction table
        return MigrationVersion.fromVersion("1.81.0");
    }

    @Override
    protected Long getInitial() {
        return LOWER_BOUND_TIMESTAMP;
    }

    @Override
    protected Optional<Long> migratePartial(Long lowerBound) {
        if (!MirrorProperties.HederaNetwork.MAINNET.equalsIgnoreCase(mirrorProperties.getNetwork())) {
            log.info("Skipping migration since it only applies to mainnet");
            return Optional.empty();
        }

        long count = 0;
        var migrationErrors = new ArrayList<String>();
        long upperBound = Math.min(lowerBound + TIMESTAMP_INCREMENT, UPPER_BOUND_TIMESTAMP);
        Map<String, Long> queryParamMap =
                Map.of("lower_bound", lowerBound, "upper_bound", upperBound, "grandfathered_id", GRANDFATHERED_ID);
        try {
            var transfers = transferJdbcTemplate.query(TRANSFER_SQL, queryParamMap, resultRowMapper);
            for (ApprovalTransfer transfer : transfers) {
                if (!isAuthorizedByContractKey(transfer, migrationErrors)) {
                    // set is_approval to true
                    String updateSql;
                    var updateParamMap = new HashMap<String, Number>();
                    updateParamMap.put("consensus_timestamp", transfer.consensusTimestamp);
                    if (transfer.transferType == TRANSFER_TYPE.CRYPTO_TRANSFER) {
                        updateSql = UPDATE_CRYPTO_TRANSFER_SQL;
                        updateParamMap.put("sender", transfer.sender);
                    } else if (transfer.transferType == TRANSFER_TYPE.NFT_TRANSFER) {
                        updateSql = UPDATE_NFT_TRANSFER_SQL;
                        updateParamMap.put("index", transfer.index);
                    } else {
                        updateSql = UPDATE_TOKEN_TRANSFER_SQL;
                        updateParamMap.put("sender", transfer.sender);
                        updateParamMap.put("token_id", transfer.tokenId);
                    }

                    transferJdbcTemplate.update(updateSql, updateParamMap);
                    count++;
                }
            }
        } catch (Exception e) {
            log.error("Error migrating synthetic transfer approvals", e);
            return Optional.empty();
        }

        log.info("Updated {} synthetic transfer approvals", count);
        migrationErrors.forEach(log::error);
        return upperBound == UPPER_BOUND_TIMESTAMP ? Optional.empty() : Optional.of(upperBound);
    }

    /**
     * A return value of false denotes that the transfer's isApproval value should be set to true
     */
    private boolean isAuthorizedByContractKey(ApprovalTransfer transfer, List<String> migrationErrors) {
        Key parsedKey;
        try {
            parsedKey = Key.parseFrom(transfer.key);
        } catch (Exception e) {
            migrationErrors.add(String.format(
                    "Unable to determine if transfer should be migrated. Entity id %d at %d: %s",
                    transfer.sender, transfer.consensusTimestamp, e.getMessage()));
            // Do not update the isApproval value
            return true;
        }

        // If the threshold is greater than one ignore it
        if (!parsedKey.hasThresholdKey() || parsedKey.getThresholdKey().getThreshold() > 1) {
            // Update the isApproval value to true
            return false;
        }

        return isAuthorizedByThresholdKey(parsedKey, transfer.contractId);
    }

    private boolean isAuthorizedByThresholdKey(Key parsedKey, long contractId) {
        var keys = parsedKey.getThresholdKey().getKeys().getKeysList();
        for (var key : keys) {
            if (key.hasContractID() && EntityId.of(key.getContractID()).getId() == contractId) {
                // Do not update the isApproval value
                return true;
            }
        }
        // Update the isApproval value to true
        return false;
    }

    @Data
    static class ApprovalTransfer {
        private Long consensusTimestamp;
        private Long contractId;
        private Integer index;
        private byte[] key;
        private Long sender;
        private Long tokenId;
        private TRANSFER_TYPE transferType;
    }
}
