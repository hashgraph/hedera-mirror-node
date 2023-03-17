package com.hedera.mirror.importer.repository;

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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.util.CollectionUtils;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;

class AddressBookServiceEndpointRepositoryTest extends AbstractRepositoryTest {

    private final EntityId addressBookEntityId102 = EntityId.of("0.0.102", EntityType.FILE);

    @Resource
    protected AddressBookServiceEndpointRepository addressBookServiceEndpointRepository;

    @Resource
    protected AddressBookEntryRepository addressBookEntryRepository;

    @Resource
    protected AddressBookRepository addressBookRepository;

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
                .containsExactlyInAnyOrder(80, 443, 8000, 8443);
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
                .containsExactlyInAnyOrder(80, 443, 80, 443, 8080, 8443, 8080, 8443, 50211, 50212, 50211, 50212);
    }

    private AddressBookServiceEndpoint addressBookServiceEndpoint(long consensusTimestamp, String ip, int port,
                                                                  long nodeId) {
        AddressBookServiceEndpoint addressBookServiceEndpoint = new AddressBookServiceEndpoint();
        addressBookServiceEndpoint.setConsensusTimestamp(consensusTimestamp);
        addressBookServiceEndpoint.setIpAddressV4(ip);
        addressBookServiceEndpoint.setPort(port);
        addressBookServiceEndpoint.setNodeId(nodeId);
        return addressBookServiceEndpoint;
    }

    private AddressBookEntry addressBookEntry(long consensusTimestamp, long nodeAccountId, List<Integer> portNums) throws UnknownHostException {
        long nodeId = nodeAccountId - 3;
        String nodeAccountIdString = String.format("0.0.%s", nodeAccountId);
        EntityId nodeAccountEntityId = EntityId.of(nodeAccountIdString, EntityType.ACCOUNT);
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .memo(nodeAccountIdString)
                .nodeAccountId(nodeAccountEntityId)
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeId(nodeId)
                .publicKey("rsa+public/key");

        if (!CollectionUtils.isEmpty(portNums)) {
            Set<AddressBookServiceEndpoint> serviceEndpoints = new HashSet<>();
            for (int i = 0; i < portNums.size(); i++) {
                serviceEndpoints.add(addressBookServiceEndpoint(
                        consensusTimestamp,
                        InetAddress.getByName("127.0.0." + i).getHostAddress(),
                        portNums.get(i),
                        nodeId));
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
