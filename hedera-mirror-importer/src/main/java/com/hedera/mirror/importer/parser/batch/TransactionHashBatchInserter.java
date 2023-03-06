package com.hedera.mirror.importer.parser.batch;

import com.google.common.base.CaseFormat;

import com.google.common.base.Stopwatch;

import com.hedera.mirror.common.domain.transaction.TransactionHash;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.ConnectionHolder;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
    private final Map<String, ConnectionHolder> threadConnections = new ConcurrentHashMap<>();

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
        if (items == null || items.isEmpty()) {
            return;
        }

//        var scheduler = Schedulers.newParallel(this.shardedTableName + "_shard_inserter", 8);
        try {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<Integer, List<TransactionHash>> shardedItems = items.stream().map(TransactionHash.class::cast)
                    .collect(Collectors.groupingBy(item -> Math.abs(item.getHash()[0] % 32)));

            Mono.when(shardedItems.entrySet()
                            .stream()
                            .map(shard -> this.processShard(shard).subscribeOn(scheduler))
                            .toList())
                    .block();

            // After parser transaction commits, commit all connections used for shards
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (ConnectionHolder holder : threadConnections.values()) {
                        try {
                            holder.getConnection().commit();
                            holder.released();
                            holder.clear();
                        } catch (SQLException e) {
                            // TODO Handle error in commit
                            throw new RuntimeException(e);
                        }
                    }
                    threadConnections.clear();
                }
            });
//            scheduler.createWorker()
            insertDurationMetric.record(stopwatch.elapsed());
            log.info("Copied {} rows from {} shards to {} table in {}", items.size(), shardedItems.size(),
                    this.shardedTableName, stopwatch);
        } catch (Exception e) {
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(),
                    this.shardedTableName));
        }
    }

    private Mono<Void> processShard(Map.Entry<Integer, List<TransactionHash>> shardData) {
        //TODO:// handle error
        return Mono.just(shardData)
                .doOnNext(data -> {

                    if (data.getValue() == null || data.getValue().isEmpty()) {
                        return;
                    }

                    ConnectionHolder holder = threadConnections.computeIfAbsent(Thread.currentThread().getName(),
                            key -> {
                        // Clean scheduler thread locals from previous run
                                TransactionSynchronizationManager.clear();
                                TransactionSynchronizationManager.getResourceMap().entrySet().forEach(TransactionSynchronizationManager::unbindResourceIfPossible);

                                //
                                TransactionSynchronizationManager.initSynchronization();
                                TransactionSynchronizationManager.setActualTransactionActive(true);
                                // Need to setup the thread locals for TransactionSynchronizationManager
                                try {
                                    //TODO
                                    DataSourceUtils.getConnection(dataSource).setAutoCommit(false);
                                } catch (SQLException e) {
                                    throw new RuntimeException(e);
                                }


                                return (ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource);
                            });

                    if (holder == null) {
                        //todo handle this
                    }

                    try {
                        BatchInserter batchInserter = batchInserters.computeIfAbsent(data.getKey(), key -> new BatchInserter(TransactionHash.class,
                                        dataSource,
                                        meterRegistry,
                                        commonParserProperties, String.format("%s_%02d", shardedTableName,
                                        data.getKey())));

                                batchInserter.persist(data.getValue());
//                        log.info("Copied {} rows to {} table in {}", data.getValue().size(), batchInserter.tableName, stopwatch);
                    } catch (Exception e) {
                        //TODO handle
                        throw new RuntimeException(e);
                    }
//                    finally {
//                        DataSourceUtils.releaseConnection(connection, dataSource);
//                    }
                }).then();
    }
}
