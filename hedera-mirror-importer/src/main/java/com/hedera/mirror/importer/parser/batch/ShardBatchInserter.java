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
import org.springframework.jdbc.datasource.DataSourceUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@CustomLog
public class ShardBatchInserter implements BatchPersister {
    // Map of table name
    private final Map<Integer, BatchInserter> batchInserters = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final CommonParserProperties commonParserProperties;
    private final Timer insertDurationMetric;
    private final String shardedTableName;

    private final ExecutorService executorService = Executors.newFixedThreadPool(16);

    public ShardBatchInserter(Class<?> entityClass, DataSource dataSource, MeterRegistry meterRegistry,
                              CommonParserProperties commonParserProperties) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.commonParserProperties = commonParserProperties;
        this.shardedTableName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entityClass.getSimpleName() + "_sharded");
        this.insertDurationMetric = Timer.builder("hedera.mirror.importer.parse.insert")
                .description("Time to insert transactions into sharded table")
                .tag("table", this.shardedTableName)
                .register(meterRegistry);
    }

    @Override
    public void persist(Collection<?> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        try {
            Stopwatch stopwatch = Stopwatch.createStarted();

            Map<Integer, List<TransactionHash>> shardedItems = items.stream()
                    .map(TransactionHash.class::cast)
                    .collect(Collectors.groupingBy((item) -> Math.abs(item.getHash()[0] % 32)));


            shardedItems.entrySet()
                    .stream()
                    .map(this::processShard)
                    .map(executorService::submit).toList()
                    .forEach(item -> {
                        try {
                            item.get();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        } catch (ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    });

            insertDurationMetric.record(stopwatch.elapsed());
            log.info("Copied {} rows from {} shards to {} in {}", items.size(), shardedItems.size(), this.shardedTableName, stopwatch);
        } catch (Exception e) {
            throw new ParserException(String.format("Error copying %d items to table %s", items.size(), this.shardedTableName));
        }
    }

    private Runnable processShard(Map.Entry<Integer, List<TransactionHash>> shardData) {
        return () -> batchInserters.computeIfAbsent(shardData.getKey(),
                (key) -> new BatchInserter(TransactionHash.class, dataSource,
                        meterRegistry, commonParserProperties,
                        String.format("%s_%02d", shardedTableName, shardData.getKey()))).persist(shardData.getValue());
    }
//    private Mono<Void> processShard(Map.Entry<Integer, List<TransactionHash>> shardData) {
//        return Mono.just(shardData)
//                .doOnNext(data -> batchInserters.computeIfAbsent(data.getKey(),
//                        (key) -> new BatchInserter(TransactionHash.class, dataSource,
//                        meterRegistry, commonParserProperties,
//                        String.format("%s_%02d", shardedTableName, shardData.getKey())))
//                        .persist(data.getValue()))
//                .subscribeOn(Schedulers.parallel())
//                .then();
//    }

}
