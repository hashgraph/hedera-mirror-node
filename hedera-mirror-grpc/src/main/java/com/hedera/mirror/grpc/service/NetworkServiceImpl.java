package com.hedera.mirror.grpc.service;

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

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Jitter;
import reactor.retry.Repeat;

import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.grpc.domain.AddressBookFilter;
import com.hedera.mirror.grpc.exception.EntityNotFoundException;
import com.hedera.mirror.grpc.repository.AddressBookEntryRepository;
import com.hedera.mirror.grpc.repository.AddressBookRepository;

@Log4j2
@Named
@RequiredArgsConstructor
@Validated
public class NetworkServiceImpl implements NetworkService {

    static final String INVALID_FILE_ID = "Not a valid address book file";
    private static final Collection<EntityId> VALID_FILE_IDS = Set.of(
            EntityId.of(0L, 0L, 101L, EntityType.FILE),
            EntityId.of(0L, 0L, 102L, EntityType.FILE)
    );

    private final AddressBookProperties addressBookProperties;
    private final AddressBookRepository addressBookRepository;
    private final AddressBookEntryRepository addressBookEntryRepository;

    @Override
    public Flux<AddressBookEntry> getNodes(AddressBookFilter filter) {
        var fileId = filter.getFileId();
        if (!VALID_FILE_IDS.contains(fileId)) {
            throw new IllegalArgumentException(INVALID_FILE_ID);
        }

        long timestamp = addressBookRepository.findLatestTimestamp(fileId.getId())
                .orElseThrow(() -> new EntityNotFoundException(fileId));
        var context = new AddressBookContext(timestamp);

        return Flux.defer(() -> page(context))
                .repeatWhen(Repeat.onlyIf(c -> !context.isComplete())
                        .randomBackoff(addressBookProperties.getMinPageDelay(), addressBookProperties.getMaxPageDelay())
                        .jitter(Jitter.random())
                        .withBackoffScheduler(Schedulers.parallel()))
                .take(filter.getLimit() > 0 ? filter.getLimit() : Long.MAX_VALUE)
                .doOnNext(context::onNext)
                .doOnSubscribe(s -> log.info("Querying for address book: {}", filter))
                .doOnComplete(() -> log.info("Retrieved {} nodes from the address book", context.getCount()));
    }

    private Flux<AddressBookEntry> page(AddressBookContext context) {
        var timestamp = context.getTimestamp();
        var nextNodeId = context.getNextNodeId();
        var pageSize = addressBookProperties.getPageSize();
        var nodes = addressBookEntryRepository.findByConsensusTimestampAndNodeId(timestamp, nextNodeId, pageSize);

        if (nodes.size() < pageSize) {
            context.completed();
        }

        log.info("Retrieved {} address book entries for timestamp {} and node ID {}",
                nodes.size(), timestamp, nextNodeId);
        return Flux.fromIterable(nodes);
    }

    @Value
    private class AddressBookContext {

        private final AtomicBoolean complete = new AtomicBoolean(false);
        private final AtomicLong count = new AtomicLong(0L);
        private final AtomicReference<AddressBookEntry> last = new AtomicReference<>();
        private final long timestamp;

        void onNext(AddressBookEntry entry) {
            count.incrementAndGet();
            last.set(entry);
        }

        long getNextNodeId() {
            AddressBookEntry entry = last.get();
            return entry != null ? entry.getNodeId() + 1 : 0L;
        }

        boolean isComplete() {
            return complete.get();
        }

        void completed() {
            complete.set(true);
        }
    }
}
