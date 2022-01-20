package com.hedera.mirror.grpc.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.grpc.GrpcIntegrationTest;

class AddressBookEntryRepositoryTest extends GrpcIntegrationTest {

    @Resource
    private AddressBookEntryRepository addressBookEntryRepository;

    @Autowired
    private DomainBuilder domainBuilder;

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
    void serviceEndpoints() {
        AddressBookEntry addressBookEntry = domainBuilder.addressBookEntry(3).persist();
        assertThat(addressBookEntryRepository.findById(addressBookEntry.getId()))
                .get()
                .extracting(AddressBookEntry::getServiceEndpoints)
                .asInstanceOf(InstanceOfAssertFactories.COLLECTION)
                .containsExactlyInAnyOrderElementsOf(addressBookEntry.getServiceEndpoints());
    }

    private AddressBookEntry addressBookEntry(long consensusTimestamp, long nodeId) {
        return domainBuilder.addressBookEntry()
                .customize(e -> e.consensusTimestamp(consensusTimestamp).nodeId(nodeId))
                .persist();
    }
}
