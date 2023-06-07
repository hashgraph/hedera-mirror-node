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
import java.util.concurrent.atomic.AtomicLong;
import lombok.SneakyThrows;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
public class SyntheticCryptoTransferApprovalMigration extends RepeatableMigration {

    private static final int BATCH_SIZE = 100;

    private static final String TRANSFER_SQL =
            """
            with ecr as (
              select e.*, cr.consensus_timestamp, cr.sender_id
              from (
                select key, id, timestamp_range from entity
                union all
                select key, id, timestamp_range from entity_history
              ) e
              join contract_result cr on cr.sender_id > 2119900 and --lower bound for sender ids where this problem occurred
              -- TODO upper bound for this migration
              lower(e.timestamp_range) <= cr.consensus_timestamp and
              (upper(e.timestamp_range) > cr.consensus_timestamp or upper(e.timestamp_range) is null)
            ),
            cryptotransfers as (
              select ct.*, ecr.sender_id, ecr.key, ecr.id
              from (
                select is_approval,
                consensus_timestamp,
                amount,
                entity_id as sender,
                cast(null as bigint) as receiver,
                cast(null as bigint) as token_id,
                cast(null as int) as index,
                'CRYPTO_TRANSFER' as transfer_type
                from crypto_transfer
              ) ct
              join ecr on ecr.consensus_timestamp = ct.consensus_timestamp and
              ecr.id = ct.sender and
              lower(ecr.timestamp_range) <= ct.consensus_timestamp and
              (upper(ecr.timestamp_range) > ct.consensus_timestamp or upper(ecr.timestamp_range) is null)
              where
                ecr.sender_id <> ct.sender and --sender is not itself
                ct.is_approval = false and
                ct.amount < 0
            ), tokentransfers as (
              select t.*, ecr.sender_id, ecr.key, ecr.id
              from (
                select is_approval,
                consensus_timestamp,
                amount,
                account_id as sender,
                cast(null as bigint) as receiver,
                token_id,
                cast(null as int) as index,
                'TOKEN_TRANSFER' as transfer_type
                from token_transfer
              ) t
              join ecr on ecr.consensus_timestamp = t.consensus_timestamp and
                ecr.id = t.sender and
                lower(ecr.timestamp_range) <= t.consensus_timestamp and
                (upper(ecr.timestamp_range) > t.consensus_timestamp or upper(ecr.timestamp_range) is null)
              where
                ecr.sender_id <> t.sender and
                t.is_approval = false and
                t.amount < 0
            ), nfttransfers as (
              select nft.*, ecr.sender_id, ecr.key, ecr.id
              from (
                select (arr.item->>'is_approval')::boolean as is_approval,
                consensus_timestamp,
                cast(null as bigint) as amount,
                (arr.item->>'receiver_account_id')::bigint as receiver,
                (arr.item->>'sender_account_id')::bigint as sender,
                cast(null as bigint) as token_id,
                arr.index,
                'NFT_TRANSFER' as transfer_type
                from transaction, jsonb_array_elements(nft_transfer) with ordinality arr(item, index)
              ) nft
              join ecr on ecr.consensus_timestamp = nft.consensus_timestamp and
                ecr.id = nft.receiver and
                lower(ecr.timestamp_range) <= nft.consensus_timestamp and
                (upper(ecr.timestamp_range) > nft.consensus_timestamp or upper(ecr.timestamp_range) is null)
              where
                ecr.sender_id <> nft.sender and
                nft.is_approval = false
            ) select * from cryptotransfers
              union all
              select * from tokentransfers
              union all
              select * from nfttransfers
              order by consensus_timestamp asc
            """;

    private static final String UPDATE_CRYPTO_TRANSFER_SQL =
            """
            update crypto_transfer set is_approval = true where amount = ? and consensus_timestamp = ? and entity_id = ?
            """;
    private static final String UPDATE_NFT_TRANSFER_SQL =
            """
            update transaction set nft_transfer = jsonb_set(nft_transfer, array[?::text, 'is_approval'], 'true', false) where consensus_timestamp = ?
            """;
    private static final String UPDATE_TOKEN_TRANSFER_SQL =
            """
            update token_transfer set is_approval = true where consensus_timestamp = ? and token_id = ? and account_id = ?
            """;

    private enum TRANSFER_TYPE {
        CRYPTO_TRANSFER,
        NFT_TRANSFER,
        TOKEN_TRANSFER
    }

    private final JdbcTemplate jdbcTemplate;

    @Lazy
    public SyntheticCryptoTransferApprovalMigration(JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
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
        AtomicLong count = new AtomicLong(0L);
        var migrationErrors = new ArrayList<String>();
        var stopwatch = Stopwatch.createStarted();
        jdbcTemplate.setFetchSize(BATCH_SIZE);
        try {
            jdbcTemplate.query(TRANSFER_SQL, rs -> {
                if (!isAuthorizedByContractKey(rs, migrationErrors)) {
                    // set is_approval to true
                    var consensusTimestamp = rs.getLong("consensus_timestamp");
                    switch (TRANSFER_TYPE.valueOf(rs.getString("transfer_type"))) {
                        case CRYPTO_TRANSFER:
                            var amount = rs.getLong("amount");
                            var entityId = rs.getLong("sender");
                            jdbcTemplate.update(UPDATE_CRYPTO_TRANSFER_SQL, amount, consensusTimestamp, entityId);
                            break;
                        case NFT_TRANSFER:
                            var index = rs.getInt("index") - 1;
                            jdbcTemplate.update(UPDATE_NFT_TRANSFER_SQL, index, consensusTimestamp);
                            break;
                        case TOKEN_TRANSFER:
                            var tokenId = rs.getLong("token_id");
                            var accountId = rs.getLong("sender");
                            jdbcTemplate.update(UPDATE_TOKEN_TRANSFER_SQL, consensusTimestamp, tokenId, accountId);
                    }
                    count.incrementAndGet();
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
     * @param rs
     * @param migrationErrors
     * @return
     */
    @SneakyThrows
    private boolean isAuthorizedByContractKey(ResultSet rs, List<String> migrationErrors) {
        var keyBytes = rs.getBytes("key");
        if (keyBytes == null) {
            return false;
        }

        Key parsedKey;
        try {
            parsedKey = Key.parseFrom(keyBytes);
        } catch (Exception e) {
            var entityId = rs.getLong("sender");
            var consensusTimestamp = rs.getLong("consensus_timestamp");
            migrationErrors.add(String.format(
                    "Unable to determine if transfer should be migrated. Entity id %d at %d: %s",
                    entityId, consensusTimestamp, e.getMessage()));
            return true;
        }

        // If the threshold is greater than one ignore it
        if (!parsedKey.hasThresholdKey() || parsedKey.getThresholdKey().getThreshold() > 1) {
            return false;
        }

        var senderId = rs.getLong("sender_id");
        return isAuthorizedByThresholdKey(parsedKey, senderId);
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
}
