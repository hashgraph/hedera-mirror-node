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

package com.hedera.mirror.importer.parser.batch;

import static com.hedera.mirror.common.domain.transaction.TransactionHash.V1_SHARD_COUNT;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.hedera.mirror.common.domain.transaction.TransactionHash;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import lombok.CustomLog;
import lombok.SneakyThrows;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@CustomLog
@Named
@Profile("!v2")
public class TransactionHashBatchInserter implements BatchPersister {
    private final Map<Integer, BatchInserter> shardBatchInserters;
    private final Timer insertDurationMetric;
    private final String shardedTableName;
    private final Scheduler scheduler;
    private final TransactionHashTxManager transactionManager;

    public TransactionHashBatchInserter(
            DataSource dataSource,
            MeterRegistry meterRegistry,
            CommonParserProperties commonParserProperties,
            TransactionHashTxManager transactionHashTxManager) {
        this.shardedTableName = CaseFormat.UPPER_CAMEL.to(
                CaseFormat.LOWER_UNDERSCORE, TransactionHash.class.getSimpleName() + "_sharded");
        this.insertDurationMetric = Timer.builder("hedera.mirror.importer.parse.insert")
                .description("Time to insert transactions into sharded table")
                .tag("table", this.shardedTableName)
                .register(meterRegistry);
        this.scheduler = Schedulers.newParallel(this.shardedTableName + "_shard_inserter", 8);
        this.transactionManager = transactionHashTxManager;

        this.shardBatchInserters = IntStream.range(0, V1_SHARD_COUNT)
                .boxed()
                .collect(ImmutableMap.toImmutableMap(
                        Function.identity(),
                        shard -> new BatchInserter(
                                TransactionHash.class,
                                dataSource,
                                meterRegistry,
                                commonParserProperties,
                                String.format("%s_%02d", shardedTableName, shard))));
    }

    @Override
    public void persist(Collection<?> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        try {
            Stopwatch stopwatch = Stopwatch.createStarted();

            // After parser transaction completes, process all transactions for shards
            transactionManager.initialize(items, this.shardedTableName);

            Map<Integer, List<TransactionHash>> shardedItems = items.stream()
                    .map(TransactionHash.class::cast)
                    .filter(TransactionHash::hashIsValid)
                    .collect(Collectors.groupingBy(TransactionHash::calculateV1Shard));

            Mono.when(shardedItems.entrySet().stream().map(this::processShard).toList())
                    .block();

            insertDurationMetric.record(stopwatch.elapsed());
            log.info(
                    "Copied {} rows from {} shards to {} table in {}",
                    items.size(),
                    shardedItems.size(),
                    this.shardedTableName,
                    stopwatch);
        } catch (Exception e) {
            throw new ParserException(
                    String.format("Error copying %d items to table %s", items.size(), this.shardedTableName));
        }
    }

    private Mono<Void> processShard(Map.Entry<Integer, List<TransactionHash>> shardData) {
        return Mono.just(shardData)
                .doOnNext(this::persist)
                .subscribeOn(scheduler)
                .then();
    }

    @SneakyThrows
    private void persist(Map.Entry<Integer, List<TransactionHash>> data) {
        var threadState = transactionManager.updateAndGetThreadState(data.getKey());
        shardBatchInserters.get(data.getKey()).persistItems(data.getValue(), threadState.getConnection());
    }

    @VisibleForTesting
    Map<String, TransactionHashTxManager.ThreadState> getThreadConnections() {
        return transactionManager.getThreadConnections();
    }
}
