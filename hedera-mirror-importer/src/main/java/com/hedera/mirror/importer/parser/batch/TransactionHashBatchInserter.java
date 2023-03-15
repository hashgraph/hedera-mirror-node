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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;

import com.google.common.base.Stopwatch;

import com.google.common.collect.Iterables;

import com.hedera.mirror.common.domain.transaction.TransactionHash;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.CustomLog;
import lombok.Data;
import lombok.SneakyThrows;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@CustomLog
public class TransactionHashBatchInserter implements BatchPersister {
    private final Map<Integer, BatchInserter> shardBatchInserters = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final CommonParserProperties commonParserProperties;
    private final Timer insertDurationMetric;
    private final String shardedTableName;

    private final Scheduler scheduler;
    private final Map<String, ThreadState> threadConnections = new ConcurrentHashMap<>();

    public TransactionHashBatchInserter(DataSource dataSource, MeterRegistry meterRegistry,
                                        CommonParserProperties commonParserProperties) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.commonParserProperties = commonParserProperties;
        this.shardedTableName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE,
                TransactionHash.class.getSimpleName() + "_sharded");
        this.insertDurationMetric = Timer.builder("hedera.mirror.importer.parse.insert")
                .description("Time to insert transactions into sharded table").tag("table", this.shardedTableName)
                .register(meterRegistry);
        this.scheduler = Schedulers.newParallel(this.shardedTableName + "_shard_inserter", 8);
    }

    @Override
    public void persist(Collection<?> items) {
        threadConnections.clear();

        if (items == null || items.isEmpty()) {
            return;
        }

        try {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<Integer, List<TransactionHash>> shardedItems = items.stream()
                    .map(TransactionHash.class::cast)
                    .collect(Collectors.groupingBy(TransactionHash::calculateV1Shard));

            // After parser transaction completes, process all transactions for shards
            addTransactionSynchronization(items);

            Mono.when(shardedItems.entrySet()
                            .stream()
                            .map(shard -> this.processShard(shard).subscribeOn(scheduler))
                            .toList())
                    .block();

            insertDurationMetric.record(stopwatch.elapsed());
            log.info("Copied {} rows from {} shards to {} table in {}", items.size(), shardedItems.size(),
                    this.shardedTableName, stopwatch);
        } catch (Exception e) {
            log.error("Got this exception cuttty", e);
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(),
                    this.shardedTableName));
        }
    }

    private void addTransactionSynchronization(Collection<?> items) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            final long itemCount = items.size();
            final long recordTimestamp = ((TransactionHash)items.iterator().next()).getConsensusTimestamp();

            @Override
            public void afterCompletion(int status) {
                var failedShards = new HashSet<Integer>();
                var successfulShards = new HashSet<Integer>();

                for (ThreadState threadState : threadConnections.values()) {
                    try (Connection connection = threadState.getConnection()) {
                        if (status == STATUS_COMMITTED) {
                            connection.commit();
                            successfulShards.addAll(threadState.getProcessedShards());
                            threadState.setStatus(STATUS_COMMITTED);
                        } else if (status == STATUS_ROLLED_BACK) {
                            connection.rollback();
                            successfulShards.addAll(threadState.getProcessedShards());
                            threadState.setStatus(STATUS_ROLLED_BACK);
                        } else {
                            connection.rollback();
                            failedShards.addAll(threadState.getProcessedShards());
                            threadState.setStatus(STATUS_UNKNOWN);
                        }
                    } catch (Exception e) {
                        log.error("Received exception processing connections for shards {} in file containing timestamp {}",
                                threadState.getProcessedShards(), recordTimestamp, e);
                        threadState.setStatus(Integer.MAX_VALUE);
                        failedShards.addAll(threadState.getProcessedShards());
                    }
                }

                String statusString = switch (status) {
                    case STATUS_COMMITTED -> "committed";
                    case STATUS_ROLLED_BACK -> "rolled back";
                    default -> "unknown";
                };

                if (failedShards.isEmpty()) {
                    log.info("Successfully {} {} items in {} shards {} in file containing timestamp {}",
                            statusString,
                            itemCount,
                            shardedTableName,
                            successfulShards,
                            recordTimestamp);
                } else {
                    log.error("Errors occurred processing sharded table {}. parent status {} successful shards {} " +
                                    "failed shards {} in file containing timestamp {}",
                            shardedTableName,
                            statusString,
                            successfulShards,
                            failedShards,
                            recordTimestamp);
                }
            }
        });
    }

    private Mono<Void> processShard(Map.Entry<Integer, List<TransactionHash>> shardData) {
        return Mono.just(shardData)
                .doOnNext(data -> {
                    configureThread(data);
                    shardBatchInserters.computeIfAbsent(data.getKey(),
                                    key -> new BatchInserter(TransactionHash.class,
                                            dataSource,
                                            meterRegistry,
                                            commonParserProperties,
                                            String.format("%s_%02d", shardedTableName, data.getKey())))
                            .persist(data.getValue());
                }).then();
    }

    private void configureThread(Map.Entry<Integer, List<TransactionHash>> data) {
        threadConnections.compute(Thread.currentThread().getName(),
                (key, value) -> {
                    if (value == null) {
                        ThreadState returnVal = setupThreadTransaction();
                        returnVal.getProcessedShards().add(data.getKey());

                        return returnVal;
                    }
                    value.getProcessedShards().add(data.getKey());
                    return value;
                });
    }

    @SneakyThrows
    private ThreadState setupThreadTransaction() {
        // Clean thread from previous run
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.unbindResourceIfPossible(dataSource);

        // initialize transaction for thread
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // Subsequent calls to get connection on this thread will use the same connection
        Connection connection = DataSourceUtils.getConnection(dataSource);
        connection.setAutoCommit(false);
        return new ThreadState(connection);
    }

    @VisibleForTesting
    Map<String, ThreadState> getThreadConnections() {
        return threadConnections;
    }

    @Data
    @VisibleForTesting
    static class ThreadState {
        private final Connection connection;
        private final Set<Integer> processedShards = new HashSet<>();
        private int status = -1;

        public ThreadState(Connection connection) {
            this.connection = connection;
        }
    }
}
