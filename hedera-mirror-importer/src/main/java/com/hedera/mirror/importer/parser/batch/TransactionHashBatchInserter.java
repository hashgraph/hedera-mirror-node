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
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.PGCopyOutputStream;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.io.IOException;
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
    }

    @Override
    public void persist(Collection<?> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        var scheduler = Schedulers.newParallel(this.shardedTableName + "_shard_inserter", 8);

        try {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<Integer, List<TransactionHash>> shardedItems = items.stream().map(TransactionHash.class::cast)
                    .collect(Collectors.groupingBy(item -> Math.abs(item.getHash()[0] % 32)));

            List<Mono<Connection>> connections = shardedItems.entrySet()
                    .stream()
                    .map(shard -> this.processShard(shard, scheduler))
                    .toList();

            Set<Connection> conns = Flux.fromIterable(connections)
                    .flatMap(Function.identity())
                    .collect(Collectors.toSet())
                    .doFinally(signal -> scheduler.dispose())
                    .block();

            // After parser transaction commits, commit all connections used for shards
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit () {
                    if (conns != null) {
                        for (Connection connection : conns) {
                            try {
                                if (!connection.isClosed()) {
                                    connection.commit();
                                    DataSourceUtils.releaseConnection(connection, dataSource);
                                }
                            } catch (SQLException e) {
                                // TODO Handle error in commit
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }

            });


            insertDurationMetric.record(stopwatch.elapsed());
            log.info("Copied {} rows from {} shards to {} table in {}", items.size(), shardedItems.size(),
                    this.shardedTableName, stopwatch);
        } catch (Exception e) {
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(),
                    this.shardedTableName));
        }
    }

    private Mono<Connection> processShard(Map.Entry<Integer, List<TransactionHash>> shardData, Scheduler scheduler) {
        //TODO:// handle error
        return Mono.just(shardData)
                .flatMap(data -> {

                    if (data.getValue() == null || data.getValue().isEmpty()) {
                        return Mono.empty();
                    }
                    Connection connection;
                    if (!TransactionSynchronizationManager.hasResource(dataSource)) {
                        connection = DataSourceUtils.getConnection(dataSource);
                        TransactionSynchronizationManager.bindResource(dataSource, connection);
                    }
                    else {
                        connection = (Connection) TransactionSynchronizationManager.getResource(dataSource);
                    }

                    try {
                        connection.setAutoCommit(false);
                        batchInserters.computeIfAbsent(data.getKey(), key -> new BatchInserter(TransactionHash.class, dataSource,
                                meterRegistry,
                                commonParserProperties, String.format("%s_%02d", shardedTableName, data.getKey()))).persistItems(data.getValue(), connection);
                        return Mono.just(connection);

                    } catch (Exception e) {
                        //TODO handle
                        return Mono.error(new RuntimeException(e));
                    }
//                    finally {
//                        DataSourceUtils.releaseConnection(connection, dataSource);
//                    }
                }).subscribeOn(scheduler);
    }
}
