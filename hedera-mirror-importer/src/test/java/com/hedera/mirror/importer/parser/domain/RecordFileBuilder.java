/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.domain;

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

/**
 * Generates record files using pre-defined templates for bulk creation of record items.
 */
@Named
@RequiredArgsConstructor
public class RecordFileBuilder {

    private final DomainBuilder domainBuilder;
    private final RecordItemBuilder recordItemBuilder;

    public Builder recordFile() {
        return new Builder();
    }

    public class Builder {

        private static final long CLOSE_INTERVAL =
                StreamType.RECORD.getFileCloseInterval().toNanos();

        private final List<ItemBuilder> itemBuilders = new ArrayList<>();

        private RecordFile previous;

        private Builder() {}

        public RecordFile build() {
            Assert.notEmpty(itemBuilders, "Must contain at least one record item");
            var recordFile = domainBuilder.recordFile();
            var recordItems = new ArrayList<RecordItem>();
            recordFile.customize(r -> r.items(Flux.fromIterable(recordItems)));

            if (previous != null) {
                recordItemBuilder.reset(Instant.ofEpochSecond(0, previous.getConsensusStart() + CLOSE_INTERVAL));
            }

            for (var itemBuilder : itemBuilders) {
                var supplier = itemBuilder.build();
                RecordItem recordItem;

                while ((recordItem = supplier.get()) != null) {
                    recordItems.add(recordItem);
                }
            }

            var consensusEnd = recordItems.get(recordItems.size() - 1).getConsensusTimestamp();
            var consensusStart = recordItems.get(0).getConsensusTimestamp();
            Instant instant = Instant.ofEpochSecond(0, consensusStart);
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);

            recordFile.customize(r -> {
                r.consensusEnd(consensusEnd)
                        .consensusStart(consensusStart)
                        .count((long) recordItems.size())
                        .name(filename);
                if (previous != null) {
                    r.index(previous.getIndex() + 1).previousHash(previous.getHash());
                }
            });

            return recordFile.get();
        }

        public Builder previous(RecordFile recordFile) {
            this.previous = recordFile;
            return this;
        }

        public Builder recordItem(RecordItemBuilder.Builder<?> recordItem) {
            return recordItems(i -> i.count(1).entities(1).template(recordItem));
        }

        public Builder recordItem(TransactionType type) {
            return recordItems(i -> i.count(1).entities(1).type(type));
        }

        public Builder recordItems(Consumer<ItemBuilder> recordItems) {
            var itemBuilder = new ItemBuilder();
            recordItems.accept(itemBuilder);
            itemBuilders.add(itemBuilder);
            return this;
        }
    }

    public class ItemBuilder {

        private final AtomicLong id = new AtomicLong(0L);
        private int count = 100;
        private int entities = 10;
        private RecordItemBuilder.Builder<?> template;
        private TransactionType type = TransactionType.CRYPTOTRANSFER;

        @Getter(lazy = true)
        private final List<RecordItemBuilder.Builder<?>> builders = createBuilders();

        private ItemBuilder() {}

        public ItemBuilder count(int count) {
            Assert.isTrue(count > 0, "count must be positive");
            this.count = count;
            return this;
        }

        public ItemBuilder entities(int entities) {
            Assert.isTrue(entities > 0, "entities must be positive");
            this.entities = entities;
            return this;
        }

        public ItemBuilder template(RecordItemBuilder.Builder<?> template) {
            this.template = template;
            return this;
        }

        public ItemBuilder type(TransactionType type) {
            Assert.notNull(type, "type must not be null");
            this.type = type;
            return this;
        }

        private Supplier<RecordItem> build() {
            var recordItemBuilders = getBuilders();
            int builderSize = recordItemBuilders.size();
            var counter = new AtomicInteger(count);

            return () -> {
                int count = counter.getAndDecrement();
                if (count <= 0) {
                    return null;
                }

                var index = count % builderSize;
                var builder = recordItemBuilders.get(index);
                return wrap(builder);
            };
        }

        private List<RecordItemBuilder.Builder<?>> createBuilders() {
            List<RecordItemBuilder.Builder<?>> builders = new ArrayList<>();
            int maxEntities = Math.min(entities, count);

            for (int i = 0; i < maxEntities; i++) {
                builders.add(template != null ? template : recordItem(type).get());
            }

            return builders;
        }

        private Supplier<RecordItemBuilder.Builder<?>> recordItem(TransactionType transactionType) {
            var supplier = recordItemBuilder.lookup(transactionType);
            if (supplier == null) {
                throw new UnsupportedOperationException("Transaction type not supported: " + transactionType);
            }
            return supplier;
        }

        private RecordItem wrap(RecordItemBuilder.Builder<?> builder) {
            var consensusTimestamp = recordItemBuilder.timestamp(ChronoUnit.NANOS);
            var validStart = Utility.instantToTimestamp(
                    Utility.convertToInstant(consensusTimestamp).minusNanos(10));
            return builder.record(r -> r.setConsensusTimestamp(consensusTimestamp))
                    .receipt(r -> r.setTopicSequenceNumber(id.getAndIncrement()))
                    .transactionBodyWrapper(tb -> tb.getTransactionIDBuilder().setTransactionValidStart(validStart))
                    .build();
        }
    }
}
