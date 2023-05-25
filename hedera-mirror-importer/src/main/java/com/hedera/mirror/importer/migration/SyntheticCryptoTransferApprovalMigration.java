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
import com.hedera.mirror.importer.config.Owner;
import com.hederahashgraph.api.proto.java.Key;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.SneakyThrows;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;

@Named
public class SyntheticCryptoTransferApprovalMigration extends RepeatableMigration {

    private static final int BATCH_SIZE = 100;

    private static final String TRANSFER_SQL =
            """
            with cryptotransfers as (
              select ct.*, cr.sender_id, e.key
              from (
                select cast(null as bigint) account_id,
                amount, --receiver
                consensus_timestamp,
                entity_id,
                is_approval,
                cast(null as bigint) as payer_account_id,
                cast(null as bigint) as receiver_account_id,
                cast(null as bigint) as serial_number,
                cast(null as bigint) as token_id,
                'CRYPTO_TRANSFER' as transfer_type
                from crypto_transfer
              ) ct
              join contract_result cr on cr.consensus_timestamp = ct.consensus_timestamp
              join entity e on e.id = ct.entity_id
              where
                cr.sender_id > 2119900 and --grandfathered number
                cr.sender_id <> ct.entity_id and --sender does not equal receiver
                ct.is_approval = false
              ), nfttransfers as (
                select nft.*, cr.sender_id, e.key
                from (
                  select cast(null as bigint) account_id,
                  cast(null as bigint) as amount,
                  consensus_timestamp,
                  cast(null as bigint) as entity_id,
                  is_approval,
                  cast(null as bigint) as payer_account_id,
                  receiver_account_id, --receiver
                  serial_number,
                  token_id,
                  'NFT_TRANSFER' as transfer_type
                  from nft_transfer
                ) nft
                join contract_result cr on cr.consensus_timestamp = nft.consensus_timestamp
                join entity e on e.id = nft.receiver_account_id
                where
                  cr.sender_id > 2119900 and
                  cr.sender_id <> nft.receiver_account_id and
                  nft.is_approval = false
            ), tokentransfers as (
              select t.*, cr.sender_id, e.key
              from (
                select account_id, --receiver
                cast(null as bigint) as amount,
                consensus_timestamp,
                cast(null as bigint) as entity_id,
                is_approval,
                payer_account_id,
                cast(null as bigint) as receiver_account_id,
                cast(null as bigint) as serial_number,
                token_id,
                'TOKEN_TRANSFER' as transfer_type
                from token_transfer
              ) t
              join contract_result cr on cr.consensus_timestamp = t.consensus_timestamp
              join entity e on e.id = t.account_id
              where
                cr.sender_id > 2119900 and
                cr.sender_id <> t.account_id and
                t.is_approval = false
            ) select * from cryptotransfers
              union all
              select * from nfttransfers
              union all
              select * from tokentransfers
              order by consensus_timestamp asc
            """;

    private static final String UPDATE_CRYPTO_TRANSFER_SQL =
            """
            update crypto_transfer set is_approval = true where amount = ? and consensus_timestamp = ? and entity_id = ?
            """;
    private static final String UPDATE_NFT_TRANSFER_SQL =
            """
            update nft_transfer set is_approval = true where consensus_timestamp = ? and serial_number = ? and token_id = ?
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
    public SyntheticCryptoTransferApprovalMigration(
            @Owner JdbcTemplate jdbcTemplate, MirrorProperties mirrorProperties) {
        super(mirrorProperties.getMigration());
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getDescription() {
        return "Update the is_approval value for synthetic crypto transfers";
    }

    @Override
    protected MigrationVersion getMinimumVersion() {
        // The version where contract_result sender_id was added
        return MigrationVersion.fromVersion("1.58.4");
    }

    @Override
    protected void doMigrate() {
        AtomicLong count = new AtomicLong(0L);
        var stopwatch = Stopwatch.createStarted();
        jdbcTemplate.setFetchSize(BATCH_SIZE);
        try {
            jdbcTemplate.query(TRANSFER_SQL, rs -> {
                if (processTransfer(rs)) {
                    var consensusTimestamp = rs.getLong("consensus_timestamp");
                    switch (TRANSFER_TYPE.valueOf(rs.getString("transfer_type"))) {
                        case CRYPTO_TRANSFER:
                            var amount = rs.getLong("amount");
                            var entityId = rs.getLong("entity_id");
                            jdbcTemplate.update(UPDATE_CRYPTO_TRANSFER_SQL, amount, consensusTimestamp, entityId);
                            break;
                        case NFT_TRANSFER:
                            var serialNumber = rs.getLong("serial_number");
                            var nftTokenId = rs.getLong("token_id");
                            jdbcTemplate.update(UPDATE_NFT_TRANSFER_SQL, consensusTimestamp, serialNumber, nftTokenId);
                            break;
                        case TOKEN_TRANSFER:
                            var tokenTransferId = rs.getLong("token_id");
                            var accountId = rs.getLong("account_id");
                            jdbcTemplate.update(
                                    UPDATE_TOKEN_TRANSFER_SQL, consensusTimestamp, tokenTransferId, accountId);
                    }
                    count.incrementAndGet();
                }
            });
        } catch (Exception e) {
            log.error("Error migrating transfer approvals", e);
            return;
        }

        log.info("Updated {} crypto transfer approvals in {}", count, stopwatch);
    }

    @SneakyThrows
    private Key getKey(byte[] bytes) {
        try {
            return Key.parseFrom(bytes);
        } catch (Exception e) {
            log.error("Unable to parse protobuf Key", e);
            throw e;
        }
    }

    @SneakyThrows
    private boolean processTransfer(ResultSet rs) {
        var senderId = rs.getLong("sender_id");
        var keyBytes = rs.getBytes("key");
        var parsedKey = getKey(keyBytes);
        return !parsedKey.hasThresholdKey() || processThresholdKey(parsedKey, senderId);
    }

    private boolean processThresholdKey(Key parsedKey, long senderId) {
        var keys = parsedKey.getThresholdKey().getKeys().getKeysList();
        for (var key : keys) {
            if (key.hasContractID() && EntityId.of(key.getContractID()).getId() != senderId) {
                // Update is_approval to true
                return true;
            }
        }

        return false;
    }
}
