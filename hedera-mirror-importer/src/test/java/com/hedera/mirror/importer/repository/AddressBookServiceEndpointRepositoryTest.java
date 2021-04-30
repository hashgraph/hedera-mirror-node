package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.AddressBookServiceEndpoint;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

class AddressBookServiceEndpointRepositoryTest extends AbstractRepositoryTest {

    private final EntityId addressBookEntityId102 = EntityId.of("0.0.102", EntityTypeEnum.FILE);

    @Resource
    protected AddressBookServiceEndpointRepository addressBookServiceEndpointRepository;

    @Resource
    protected AddressBookEntryRepository addressBookEntryRepository;

    @Resource
    protected AddressBookRepository addressBookRepository;

    @Test
    void save() {
        long consensusTimestamp = 1L;
        AddressBookServiceEndpoint addressBookServiceEndpoint = addressBookServiceEndpointRepository.save(
                addressBookServiceEndpoint(consensusTimestamp, "127.0.0.1", 443, 3));
        assertThat(addressBookServiceEndpointRepository.findById(addressBookServiceEndpoint.getId()))
                .get()
                .isEqualTo(addressBookServiceEndpoint);
    }

    @Test
    void verifySequence() {
        long consensusTimestamp = 1L;
        addressBookServiceEndpointRepository.save(addressBookServiceEndpoint(consensusTimestamp, "127.0.0.1", 80, 3));
        addressBookServiceEndpointRepository.save(addressBookServiceEndpoint(consensusTimestamp, "127.0.0.2", 443, 3));
        addressBookServiceEndpointRepository.save(addressBookServiceEndpoint(consensusTimestamp, "127.0.0.3", 8000, 4));
        addressBookServiceEndpointRepository.save(addressBookServiceEndpoint(consensusTimestamp, "127.0.0.4", 8443, 4));
        assertThat(addressBookServiceEndpointRepository.findAll())
                .isNotNull()
                .hasSize(4)
                .extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getPort)
                .containsSequence(80, 443, 8000, 8443);
    }

    @Test
    void verifyEntryToServiceEndpointMapping() throws UnknownHostException {
        long consensusTimestamp = 1L;
        addressBookRepository.save(addressBook(1, Collections.emptyList(), Collections.emptyList()));
        addressBookEntryRepository.save(addressBookEntry(consensusTimestamp, 3, List.of(80, 443)));
        addressBookEntryRepository.save(addressBookEntry(consensusTimestamp, 4, List.of(8000, 8443)));
        assertThat(addressBookEntryRepository.findAll())
                .isNotNull()
                .hasSize(2);
        assertThat(addressBookServiceEndpointRepository.findAll())
                .isNotNull()
                .hasSize(4)
                .extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getPort)
                .containsSequence(80, 443, 8000, 8443);
    }

    @Test
    void verifyAddressBookToServiceEndpointMapping() throws UnknownHostException {
        addressBookRepository.save(addressBook(1, List.of(3, 4), List.of(80, 443)));
        addressBookRepository.save(addressBook(2, List.of(5, 6), List.of(8080, 8443)));
        addressBookRepository.save(addressBook(3, List.of(7, 8), List.of(50211, 50212)));
        assertThat(addressBookRepository.findAll())
                .isNotNull()
                .hasSize(3);
        assertThat(addressBookEntryRepository.findAll())
                .isNotNull()
                .hasSize(6);
        assertThat(addressBookServiceEndpointRepository.findAll())
                .isNotNull()
                .hasSize(12)
                .extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getPort)
                .containsSequence(80, 443, 80, 443, 8080, 8443, 8080, 8443, 50211, 50212, 50211, 50212);
    }

    private AddressBookServiceEndpoint addressBookServiceEndpoint(long consensusTimestamp, String ip, int port,
                                                                  long nodeAccountId) {
        String nodeAccountIdString = String.format("0.0.%s", nodeAccountId);
        return new AddressBookServiceEndpoint(
                consensusTimestamp,
                ip,
                port,
                EntityId.of(nodeAccountIdString, EntityTypeEnum.ACCOUNT));
    }

    private AddressBookEntry addressBookEntry(long consensusTimestamp, long nodeAccountId, List<Integer> portNums) throws UnknownHostException {
        String nodeAccountIdString = String.format("0.0.%s", nodeAccountId);
        EntityId nodeAccountEntityId = EntityId.of(nodeAccountIdString, EntityTypeEnum.ACCOUNT);
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .id(new AddressBookEntry.Id(consensusTimestamp, nodeAccountId - 3))
                .publicKey("rsa+public/key")
                .memo(nodeAccountIdString)
                .nodeAccountId(nodeAccountEntityId)
                .nodeCertHash("nodeCertHash".getBytes());

        if (!CollectionUtils.isEmpty(portNums)) {
            List<AddressBookServiceEndpoint> serviceEndpoints = new ArrayList<>();
            for (int i = 0; i < portNums.size(); i++) {
                serviceEndpoints.add(addressBookServiceEndpoint(
                        consensusTimestamp,
                        InetAddress.getByName("127.0.0." + i).getHostAddress(),
                        portNums.get(i),
                        nodeAccountId));
            }

            builder.serviceEndpoints(serviceEndpoints);
        }

        return builder.build();
    }

    private AddressBook addressBook(long consensusTimestamp, List<Integer> accountNums, List<Integer> portNums) throws UnknownHostException {

        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .startConsensusTimestamp(consensusTimestamp)
                .fileData("address book memo".getBytes())
                .fileId(addressBookEntityId102);

        List<AddressBookEntry> addressBookEntries = new ArrayList<>();
        for (Integer accountNum : accountNums) {
            addressBookEntries.add(addressBookEntry(consensusTimestamp, accountNum, portNums));
        }
        builder.entries(addressBookEntries);

        return builder.build();
    }
}
