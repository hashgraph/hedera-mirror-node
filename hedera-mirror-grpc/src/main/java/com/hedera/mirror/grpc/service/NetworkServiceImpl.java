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
import com.hedera.mirror.common.domain.addressbook.NodeStake;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.grpc.domain.AddressBookFilter;
import com.hedera.mirror.grpc.exception.EntityNotFoundException;
import com.hedera.mirror.grpc.repository.AddressBookEntryRepository;
import com.hedera.mirror.grpc.repository.AddressBookRepository;
import com.hedera.mirror.grpc.repository.NodeStakeRepository;
import jakarta.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
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
    private static final Collection<EntityId> VALID_FILE_IDS =
            Set.of(EntityId.of(0L, 0L, 101L, EntityType.FILE), EntityId.of(0L, 0L, 102L, EntityType.FILE));

    private final AddressBookProperties addressBookProperties;
    private final AddressBookRepository addressBookRepository;
    private final AddressBookEntryRepository addressBookEntryRepository;
    private final NodeStakeRepository nodeStakeRepository;
    private final TransactionOperations transactionOperations;

    private final AtomicReference<Map<Long, NodeStake>> nodeStakeCacheMapRef =
            new AtomicReference<>(Collections.emptyMap());

    @Override
    public Flux<AddressBookEntry> getNodes(AddressBookFilter filter) {
        var fileId = filter.getFileId();
        if (!VALID_FILE_IDS.contains(fileId)) {
            throw new IllegalArgumentException(INVALID_FILE_ID);
        }

        long timestamp = addressBookRepository
                .findLatestTimestamp(fileId.getId())
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
        return transactionOperations.execute(t -> {
            var timestamp = context.getTimestamp();
            var nextNodeId = context.getNextNodeId();
            var pageSize = addressBookProperties.getPageSize();
            var nodes = addressBookEntryRepository.findByConsensusTimestampAndNodeId(timestamp, nextNodeId, pageSize);
            var endpoints = new AtomicInteger(0);

            nodes.forEach(node -> {
                // Replace node stake with latest present in node_stake table
                node.setStake(getStakeForNode(node.getNodeId()));
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
                    timestamp,
                    nextNodeId);
            return Flux.fromIterable(nodes);
        });
    }

    @Scheduled(fixedRateString = "#{@addressBookProperties.getNodeStakeCacheRefreshFrequency().toMillis()}")
    public void reloadNodeStakeCache() {
        var latestNodeStake = nodeStakeRepository.findLatest();
        log.info("Reloading node stake cache with {} entries", latestNodeStake.size());
        this.nodeStakeCacheMapRef.set(latestNodeStake.stream()
                .collect(Collectors.toUnmodifiableMap(NodeStake::getNodeId, Function.identity())));
    }

    private long getStakeForNode(long nodeId) {
        var nodeStake = nodeStakeCacheMapRef.get().get(nodeId);
        return nodeStake != null ? nodeStake.getStake() : 0L;
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
