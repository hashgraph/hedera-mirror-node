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

package com.hedera.mirror.grpc.service;

import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.grpc.domain.AddressBookFilter;
import com.hedera.mirror.grpc.exception.EntityNotFoundException;
import com.hedera.mirror.grpc.repository.AddressBookEntryRepository;
import com.hedera.mirror.grpc.repository.AddressBookRepository;
import com.hedera.mirror.grpc.repository.NodeStakeRepository;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.retry.Jitter;
import reactor.retry.Repeat;

@Log4j2
@Named
@RequiredArgsConstructor
@Validated
public class NetworkServiceImpl implements NetworkService {

    static final String INVALID_FILE_ID = "Not a valid address book file";
    private static final long NODE_STAKE_EMPTY_TABLE_TIMESTAMP = 0L;
    private static final Collection<EntityId> VALID_FILE_IDS =
            Set.of(EntityId.of(0L, 0L, 101L, EntityType.FILE), EntityId.of(0L, 0L, 102L, EntityType.FILE));

    private final AddressBookProperties addressBookProperties;
    private final AddressBookRepository addressBookRepository;
    private final AddressBookEntryRepository addressBookEntryRepository;
    private final NodeStakeRepository nodeStakeRepository;

    @Qualifier("readOnly")
    private final TransactionOperations transactionOperations;

    @Override
    public Flux<AddressBookEntry> getNodes(AddressBookFilter filter) {
        var fileId = filter.getFileId();
        if (!VALID_FILE_IDS.contains(fileId)) {
            throw new IllegalArgumentException(INVALID_FILE_ID);
        }

        long addressBookTimestamp = addressBookRepository
                .findLatestTimestamp(fileId.getId())
                .orElseThrow(() -> new EntityNotFoundException(fileId));
        long nodeStakeTimestamp = nodeStakeRepository.findLatestTimestamp().orElse(NODE_STAKE_EMPTY_TABLE_TIMESTAMP);
        var nodeStakeMap = nodeStakeRepository.findAllStakeByConsensusTimestamp(nodeStakeTimestamp);
        var context = new AddressBookContext(addressBookTimestamp, nodeStakeMap);

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
        return transactionOperations.execute(t -> {
            var addressBookTimestamp = context.getAddressBookTimestamp();
            var nodeStakeMap = context.getNodeStakeMap();
            var nextNodeId = context.getNextNodeId();
            var pageSize = addressBookProperties.getPageSize();
            var nodes = addressBookEntryRepository.findByConsensusTimestampAndNodeId(
                    addressBookTimestamp, nextNodeId, pageSize);
            var endpoints = new AtomicInteger(0);

            nodes.forEach(node -> {
                // Override node stake
                node.setStake(nodeStakeMap.getOrDefault(node.getNodeId(), 0L));
                // This hack ensures that the nested serviceEndpoints is loaded eagerly and voids lazy init exceptions
                endpoints.addAndGet(node.getServiceEndpoints().size());
            });

            if (nodes.size() < pageSize) {
                context.completed();
            }

            log.info(
                    "Retrieved {} address book entries and {} endpoints for timestamp {} and node ID {}",
                    nodes.size(),
                    endpoints,
                    addressBookTimestamp,
                    nextNodeId);
            return Flux.fromIterable(nodes);
        });
    }

    @Value
    private static class AddressBookContext {

        private final AtomicBoolean complete = new AtomicBoolean(false);
        private final AtomicLong count = new AtomicLong(0L);
        private final AtomicReference<AddressBookEntry> last = new AtomicReference<>();
        private final long addressBookTimestamp;
        private final Map<Long, Long> nodeStakeMap;

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
