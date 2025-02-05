/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.importer.DisableRepeatableSqlMigration;
import com.hedera.mirror.importer.EnabledIfV2;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import com.hedera.mirror.importer.repository.TransactionHashRepository;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.StreamUtils;

@DisablePartitionMaintenance
@DisableRepeatableSqlMigration
@EnabledIfV2
@RequiredArgsConstructor
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=2.4.0")
class ChangeTransactionHashDistributionMigrationTest extends ImporterIntegrationTest {

    private static final String REVERT_DDL =
            """
            select alter_distributed_table('transaction_hash', distribution_column := 'hash');
            alter table transaction_hash drop column distribution_id;
            """;

    private final TransactionHashRepository transactionHashRepository;

    @Value("classpath:db/migration/v2/V2.4.1__change_transaction_hash_distribution.sql")
    private final Resource migrationSql;

    @AfterEach
    void cleanup() {
        ownerJdbcTemplate.execute(REVERT_DDL);
    }

    @Test
    void empty() {
        runMigration();
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void migrate() {
        // given
        // note the distributionId is already calculated from the hash
        var transactionHashes = List.of(
                domainBuilder.transactionHash().get(),
                domainBuilder.transactionHash().get(),
                domainBuilder
                        .transactionHash()
                        .customize(th -> th.hash(domainBuilder.bytes(32)))
                        .get());
        persistTransactionHashes(transactionHashes);

        // when
        runMigration();

        // then
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactionHashes);
    }

    private void persistTransactionHashes(Collection<TransactionHash> transactionHashes) {
        ownerJdbcTemplate.batchUpdate(
                """
                    insert into transaction_hash (consensus_timestamp, hash, payer_account_id) values (?, ?, ?)
                    """,
                transactionHashes,
                transactionHashes.size(),
                (ps, transactionHash) -> {
                    ps.setLong(1, transactionHash.getConsensusTimestamp());
                    ps.setBytes(2, transactionHash.getHash());
                    ps.setLong(3, transactionHash.getPayerAccountId());
                });
    }

    @SneakyThrows
    private void runMigration() {
        try (var is = migrationSql.getInputStream()) {
            var script = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            ownerJdbcTemplate.execute(script);
        }
    }
}
