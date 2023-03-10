package com.hedera.mirror.importer.parser.batch;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;

import com.google.common.base.Stopwatch;

import com.hedera.mirror.common.domain.transaction.TransactionHash;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.CustomLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@CustomLog
public class TransactionHashBatchInserter implements BatchPersister {
    // Map of table name
    private final Map<Integer, BatchInserter> batchInserters = new ConcurrentHashMap<>();
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
                    .collect(Collectors.groupingBy(item -> Math.abs(item.getHash()[0] % 32)));

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
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(),
                    this.shardedTableName));
        }
    }

    private void addTransactionSynchronization(Collection<?> items) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            final long itemCount = items.size();
            final long minConsensusTimestamp = items.stream()
                    .map(TransactionHash.class::cast)
                    .mapToLong(TransactionHash::getConsensusTimestamp)
                    .min()
                    .orElse(0);

            final long maxConsensusTimestamp = items.stream()
                    .map(TransactionHash.class::cast)
                    .mapToLong(TransactionHash::getConsensusTimestamp)
                    .max()
                    .orElse(0);

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
                        log.error("Received exception processing connections for shards {} from {} to {}",
                                threadState.getProcessedShards(), minConsensusTimestamp, maxConsensusTimestamp, e);
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
                    log.info("Successfully {} {} items in shards {} from {} to {}",
                            statusString,
                            itemCount,
                            successfulShards,
                            minConsensusTimestamp,
                            maxConsensusTimestamp);
                } else {
                    log.error("Errors occurred processing shard transactions. parent status {} successful shards {} " +
                                    "failed shards {} from={} to={}",
                            statusString,
                            successfulShards,
                            failedShards,
                            minConsensusTimestamp,
                            maxConsensusTimestamp);
                }
            }
        });
    }

    private Mono<Void> processShard(Map.Entry<Integer, List<TransactionHash>> shardData) {
        return Mono.just(shardData)
                .doOnNext(data -> {

                    if (data.getValue() == null || data.getValue().isEmpty()) {
                        return;
                    }

                    configureThread(data);

                    batchInserters.computeIfAbsent(data.getKey(),
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

    private ThreadState setupThreadTransaction() {
        // Clean thread from previous run
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.unbindResourceIfPossible(dataSource);

        try {
            // initialize transaction for thread
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);

            // Subsequent calls to get connection on this thread will use the same connection
            Connection connection = DataSourceUtils.getConnection(dataSource);
            connection.setAutoCommit(false);
            return new ThreadState(connection);
        } catch (SQLException e) {
            log.error("Unable to configure autoCommit", e);
            throw new ParserException(e);
        }
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
