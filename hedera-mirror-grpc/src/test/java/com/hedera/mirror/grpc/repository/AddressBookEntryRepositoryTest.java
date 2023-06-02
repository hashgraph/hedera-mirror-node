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

package com.hedera.mirror.grpc.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.grpc.GrpcIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class AddressBookEntryRepositoryTest extends GrpcIntegrationTest {

    private final AddressBookEntryRepository addressBookEntryRepository;

    @Test
    void findByConsensusTimestampAndNodeId() {
        long consensusTimestamp = 1L;
        int limit = 2;
        AddressBookEntry addressBookEntry1 = addressBookEntry(consensusTimestamp, 0L);
        AddressBookEntry addressBookEntry2 = addressBookEntry(consensusTimestamp, 1L);
        AddressBookEntry addressBookEntry3 = addressBookEntry(consensusTimestamp, 2L);
        addressBookEntry(consensusTimestamp + 1, 0L);

        assertThat(addressBookEntryRepository.findByConsensusTimestampAndNodeId(consensusTimestamp, 0L, limit))
                .as("First page has a length equal to limit")
                .hasSize(limit)
                .containsExactly(addressBookEntry1, addressBookEntry2);

        assertThat(addressBookEntryRepository.findByConsensusTimestampAndNodeId(consensusTimestamp, limit, limit))
                .as("Second page has less than limit")
                .containsExactly(addressBookEntry3);
    }

    @Test
    @Transactional
    void serviceEndpoints() {
        AddressBookEntry addressBookEntry = domainBuilder.addressBookEntry(3).persist();
        assertThat(addressBookEntryRepository.findById(addressBookEntry.getId()))
                .get()
                .extracting(AddressBookEntry::getServiceEndpoints)
                .asInstanceOf(InstanceOfAssertFactories.COLLECTION)
                .containsExactlyInAnyOrderElementsOf(addressBookEntry.getServiceEndpoints());
    }

    private AddressBookEntry addressBookEntry(long consensusTimestamp, long nodeId) {
        return domainBuilder
                .addressBookEntry()
                .customize(e -> e.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                .persist();
    }
}
