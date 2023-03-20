package com.hedera.mirror.importer.parser.batch;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.repository.TransactionHashRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.transaction.reactive.TransactionSynchronization.STATUS_COMMITTED;
import static org.springframework.transaction.reactive.TransactionSynchronization.STATUS_ROLLED_BACK;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TransactionHashBatchInserterTest extends IntegrationTest {
    private final JdbcTemplate jdbcTemplate;
    private final BatchPersister batchPersister;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionHashBatchInserter hashBatchInserter;
    private final DataSource datasource;
    private final TransactionHashRepository transactionHashRepository;
    private Set<TransactionHash> transactionHashes;
    private Set<Transaction> transactions;
    private Map<Integer, List<TransactionHash>> shardMap;

    @BeforeEach
    void setup() {
        transactionHashes = transactionHash(64);
        transactions = transactions(10);
        shardMap = transactionHashes.stream()
                .collect(Collectors.groupingBy(TransactionHash::calculateV1Shard));
    }

    @Test
    void persist() {
        //Execute inside a parent transaction
        transactionTemplate.executeWithoutResult(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(transactionHashes);
            // Inside a different transaction so will not be available when queried here
            shardMap.keySet()
                    .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());
            assertThat(transactionHashRepository.findAll()).isEmpty();

            assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
            hashBatchInserter.getThreadConnections().values()
                    .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));
        });
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

        // Now that parent transaction has committed, the transactions for threads will be committed
        assertThreadState(STATUS_COMMITTED);

        shardMap.keySet()
                .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).containsExactlyInAnyOrderElementsOf(shardMap.get(shard)));
        assertThat(transactionHashRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactionHashes);
    }

    @Test
    void persistEmpty() {
        //Execute inside a parent transaction
        transactionTemplate.executeWithoutResult(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            hashBatchInserter.persist(Collections.emptyList());
            hashBatchInserter.persist(null);
            assertThat(hashBatchInserter.getThreadConnections()).isEmpty();
        });
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);
        assertThat(hashBatchInserter.getThreadConnections()).isEmpty();
        assertThat(transactionHashRepository.findAll()).isEmpty();
    }

    @Test
    void persistParentFails() {
        //Execute inside a parent transaction
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(transactionHashes);
            shardMap.keySet()
                    .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());

            assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
            hashBatchInserter.getThreadConnections().values()
                    .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));

            throw new RuntimeException("intentional");
        })).hasMessage("intentional");

        assertThat(transactionRepository.findAll()).isEmpty();
        assertThreadState(STATUS_ROLLED_BACK);
        shardMap.keySet()
                .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());
    }

    @Test
    void persistParentTransactionClosedBeforeCommit() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(transactionHashes);
            shardMap.keySet()
                    .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());

            assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
            hashBatchInserter.getThreadConnections().values()
                    .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));

            // Get holder of parent transaction
            ConnectionHolder connectionHolder =
                    (ConnectionHolder) TransactionSynchronizationManager.getResource(datasource);
            markAndCloseConnection(connectionHolder.getConnection());
        })).hasMessageStartingWith("Unable to commit against JDBC Connection");

        assertThat(transactionRepository.findAll()).isEmpty();
        assertThreadState(STATUS_ROLLED_BACK);
        shardMap.keySet()
                .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());
    }

    @Test
    void persistChildTransactionClosedBeforeCommit() {
        //Execute inside a parent transaction
        String closedThread = transactionTemplate.execute(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(transactionHashes);
            shardMap.keySet()
                    .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());

            assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
            hashBatchInserter.getThreadConnections().values()
                    .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));

            // Get connection of thread transaction
            var key = hashBatchInserter.getThreadConnections().keySet().iterator().next();
            var threadToClose = hashBatchInserter.getThreadConnections().get(key);
            markAndCloseConnection(threadToClose.getConnection());
            return key;
        });

        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

        var closedThreadState = hashBatchInserter.getThreadConnections().get(closedThread);
        closedThreadState.getProcessedShards()
                .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());
        assertThat(closedThreadState.getStatus()).isEqualTo(Integer.MAX_VALUE);

        //Remove the dead connection to assert remaining shards
        hashBatchInserter.getThreadConnections().remove(closedThread);
        assertThreadState(STATUS_COMMITTED);
    }

    @Test
    void persistTransactionHashInvalid() {
        //Execute inside a parent transaction
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            transactionHashes.iterator().next().setHash(null);
            batchPersister.persist(transactionHashes);
            shardMap.keySet()
                    .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());

            assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
            hashBatchInserter.getThreadConnections().values()
                    .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));
        })).isInstanceOf(ParserException.class);

        assertThat(hashBatchInserter.getThreadConnections()).isEmpty();
        shardMap.keySet()
                .forEach(shard -> assertThat(TestUtils.getShardTransactionHashes(shard, jdbcTemplate)).isEmpty());
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    private void assertThreadState(int statusCommitted) {
        assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
        hashBatchInserter.getThreadConnections()
                .values()
                .forEach(threadState -> assertThreadState(statusCommitted, threadState));
    }

    @SneakyThrows
    private void assertThreadState(int statusCommitted, TransactionHashBatchInserter.ThreadState threadState) {
        assertThat(threadState.getStatus()).isEqualTo(statusCommitted);
        assertThat(threadState.getConnection().isClosed()).isTrue();
    }

    @SneakyThrows
    private void markAndCloseConnection(Connection connection) {
        connection.getMetaData();
        connection.close();
    }

    private Set<TransactionHash> transactionHash(int count) {
        var returnVal = new HashSet<TransactionHash>();
        var consensusTimestamp = Instant.now().toEpochMilli();

        for (int i = 0; i < count; i++) {
            returnVal.add(TransactionHash.builder()
                    .consensusTimestamp(consensusTimestamp * 1_000_000L + i)
                    .payerAccountId(1)
                    .hash(domainBuilder.transactionHash().get().getHash())
                    .build());
        }
        return returnVal;
    }

    private Set<Transaction> transactions(int count) {
        var returnVal = new HashSet<Transaction>();
        for (int i = 0; i < count; i++) {
            returnVal.add(domainBuilder.transaction().get());
        }

        return returnVal;
    }
}
