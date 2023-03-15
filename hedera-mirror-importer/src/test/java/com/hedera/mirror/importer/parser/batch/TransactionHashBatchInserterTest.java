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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.Utility;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.transaction.reactive.TransactionSynchronization.STATUS_COMMITTED;
import static org.springframework.transaction.reactive.TransactionSynchronization.STATUS_ROLLED_BACK;

@EnabledIfV1
class TransactionHashBatchInserterTest extends IntegrationTest {
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private BatchPersister batchPersister;

    @Resource
    private TransactionRepository transactionRepository;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private TransactionHashBatchInserter hashBatchInserter;

    @Resource
    private DataSource datasource;

    private final Set<TransactionHash> transactionHashes = transactionHash(25);
    private final Set<Transaction> transactions = transactions(10);
    private final Map<Integer, List<TransactionHash>> shardMap = transactionHashes.stream()
    .collect(Collectors.groupingBy(TransactionHash::calculateV1Shard));

    @Test
    void persist() {
        //Execute inside a parent transaction
        transactionTemplate.execute(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(transactionHashes);
            // Inside a different transaction so will not be available when queried here
            shardMap.keySet()
                    .forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());

            assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
            hashBatchInserter.getThreadConnections().values()
                    .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));
            return new HashSet<>(); //ignored
        });
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

        // Now that parent transaction has committed, the transactions for threads will be committed
        assertThreadState(STATUS_COMMITTED);

        shardMap.keySet()
                .forEach(shard -> assertThat(getShardTransactionHashes(shard)).containsExactlyInAnyOrderElementsOf(shardMap.get(shard)));
    }

    @Test
    void persistEmpty() {
        //Execute inside a parent transaction
        transactionTemplate.execute(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            hashBatchInserter.persist(Collections.emptyList());
            hashBatchInserter.persist(null);
            assertThat(hashBatchInserter.getThreadConnections()).isEmpty();
            return new HashSet<>(); //ignored
        });
        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);
        assertThat(hashBatchInserter.getThreadConnections()).isEmpty();
    }

    @Test
    void persistParentFails() {
        //Execute inside a parent transaction
        try {
            transactionTemplate.execute(status -> {
                batchPersister.persist(transactions);
                assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

                batchPersister.persist(transactionHashes);
                shardMap.keySet()
                        .forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());

                assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
                hashBatchInserter.getThreadConnections().values()
                        .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));

                throw new RuntimeException("intentional");
            });
        } catch (RuntimeException e) {
            if (!e.getMessage().equalsIgnoreCase("intentional")) {
                throw e;
            }
        }

        assertThat(transactionRepository.findAll()).isEmpty();
        assertThreadState(STATUS_ROLLED_BACK);
        shardMap.keySet()
                .forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());
    }

    @Test
    void persistParentTransactionClosedBeforeCommit() {
        //Execute inside a parent transaction
        try {
            transactionTemplate.execute((TransactionCallback<Set<TransactionHash>>) status -> {
                batchPersister.persist(transactions);
                assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

                batchPersister.persist(transactionHashes);
                shardMap.keySet()
                        .forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());

                assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
                hashBatchInserter.getThreadConnections().values()
                        .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));

                // Get holder of parent transaction
                ConnectionHolder connectionHolder =
                        (ConnectionHolder) TransactionSynchronizationManager.getResource(datasource);
                try {
                    connectionHolder.getConnection().close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return new HashSet<>(); //ignored
            });
        } catch (Exception e) {
            // intentional
            if (!e.getMessage().startsWith("Unable to commit against JDBC Connection")) {
                throw e;
            }
        }

        assertThat(transactionRepository.findAll()).isEmpty();
        assertThreadState(STATUS_ROLLED_BACK);
        shardMap.keySet()
                .forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());
    }

    @Test
    void persistChildTransactionClosedBeforeCommit() {
        //Execute inside a parent transaction
        String closedThread = transactionTemplate.execute(status -> {
            batchPersister.persist(transactions);
            assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

            batchPersister.persist(transactionHashes);
            shardMap.keySet()
                    .forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());

            assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
            hashBatchInserter.getThreadConnections().values()
                    .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));

            // Get connection of thread transaction
            var key = hashBatchInserter.getThreadConnections().keySet().iterator().next();
            var threadToClose = hashBatchInserter.getThreadConnections().get(key);
            try {
                //Mark as dirty
                threadToClose.getConnection().getMetaData();
                threadToClose.getConnection().close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            return key;
        });

        assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

        var closedThreadState = hashBatchInserter.getThreadConnections().get(closedThread);
        closedThreadState.getProcessedShards().forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());
        assertThat(closedThreadState.getStatus()).isEqualTo(Integer.MAX_VALUE);

        //Remove the dead connection to assert remaining shards
        hashBatchInserter.getThreadConnections().remove(closedThread);
        assertThreadState(STATUS_COMMITTED);
    }

    @Test
    void persistTransactionHashInvalid() {
        //Execute inside a parent transaction
        try {
            transactionTemplate.execute(status -> {
                batchPersister.persist(transactions);
                assertThat(transactionRepository.findAll()).containsExactlyInAnyOrderElementsOf(transactions);

                var copy = new HashSet<>(transactionHashes);
                copy.iterator().next().setHash(null);

                batchPersister.persist(copy);
                shardMap.keySet()
                        .forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());

                assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
                hashBatchInserter.getThreadConnections().values()
                        .forEach(threadState -> assertThat(threadState.getStatus()).isEqualTo(-1));
                return new HashSet<>(); //ignored
            });
        } catch (ParserException e) {
            assertThat(hashBatchInserter.getThreadConnections()).isEmpty();
            shardMap.keySet()
                    .forEach(shard -> assertThat(getShardTransactionHashes(shard)).isEmpty());
            assertThat(transactionRepository.findAll()).isEmpty();
            return;
        }
        throw new RuntimeException("I should have asserted status on parser exception");
    }

    private void assertThreadState(int statusCommitted) {
        assertThat(hashBatchInserter.getThreadConnections()).isNotEmpty();
        hashBatchInserter.getThreadConnections()
                .values()
                .forEach(threadState -> {
                    assertThat(threadState.getStatus()).isEqualTo(statusCommitted);
                    try {
                        assertThat(threadState.getConnection().isClosed()).isTrue();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private List<TransactionHash> getShardTransactionHashes(int shard) {
        var sql = String.format("SELECT * from transaction_hash_sharded_%02d", shard);
        return jdbcTemplate.query(sql, new DataClassRowMapper<>(TransactionHash.class));
    }

    private Set<TransactionHash> transactionHash(int count) {
        var returnVal = new HashSet<TransactionHash>();
        var consensusTimestamp = Instant.now().toEpochMilli();

        for (int i = 0; i < count; i++) {
            returnVal.add(TransactionHash.builder()
                    .consensusTimestamp(consensusTimestamp * 1_000_000L + i)
                    .payerAccountId(1)
                    .hash(DigestUtils.sha384(
                            Utility.instantToTimestamp(Instant.ofEpochMilli(consensusTimestamp).plusNanos(i))
                                    .toByteArray()
                    ))
                    .build());
        }
        return returnVal;
    }

    private Set<Transaction> transactions(int count) {
        var returnVal = new HashSet<Transaction>();

        var consensusTimestamp = Instant.now().toEpochMilli();
        for (int i = 0; i < count; i++) {
            EntityId entityId = EntityId.of(10, 10, 10, ACCOUNT);
            Transaction transaction = new Transaction();
            transaction.setConsensusTimestamp(consensusTimestamp * 1_000_000L + i);
            transaction.setEntityId(entityId);
            transaction.setNodeAccountId(entityId);
            transaction.setMemo("memo".getBytes());
            transaction.setNonce(0);
            transaction.setType(14);
            transaction.setResult(22);
            transaction.setTransactionHash("transaction hash".getBytes());
            transaction.setTransactionBytes("transaction bytes".getBytes());
            transaction.setPayerAccountId(entityId);
            transaction.setValidStartNs(consensusTimestamp * 1_000_000L);
            transaction.setValidDurationSeconds(1L);
            transaction.setMaxFee(1L);
            transaction.setChargedTxFee(1L);
            transaction.setInitialBalance(0L);
            returnVal.add(transaction);
        }

        return returnVal;
    }
}
