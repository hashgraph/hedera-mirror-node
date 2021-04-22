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
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

public class AddressBookRepositoryTest extends AbstractRepositoryTest {

    @Resource
    protected AddressBookRepository addressBookRepository;

    private final EntityId addressBookEntityId101 = EntityId.of("0.0.101", EntityTypeEnum.FILE);
    private final EntityId addressBookEntityId102 = EntityId.of("0.0.102", EntityTypeEnum.FILE);

    @Test
    void save() {
        AddressBook addressBook = addressBookRepository.save(addressBook(null, 4, 4));
        addressBooksMatch(addressBook, addressBookRepository.findById(addressBook.getStartConsensusTimestamp())
                .get());
    }

    @Test
    void findLatestAddressBook() {
        addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId102), 1, 2));
        addressBookRepository.save(addressBook(ab -> ab.fileId(addressBookEntityId101), 3, 4));
        AddressBook addressBook = addressBookRepository
                .save(addressBook(ab -> ab.fileId(addressBookEntityId102), 5, 6));
        assertThat(addressBookRepository
                .findLatestAddressBook(7L, addressBookEntityId102.getId()))
                .get()
                .isNotNull()
                .extracting(AddressBook::getStartConsensusTimestamp)
                .isEqualTo(6L);
    }

    private AddressBook addressBook(Consumer<AddressBook.AddressBookBuilder> addressBookCustomizer,
                                    long consensusTimestamp, int nodeCount) {
        long startConsensusTimestamp = consensusTimestamp + 1;
        List<AddressBookEntry> addressBookEntryList = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            long id = i;
            long nodeId = 3 + i;
            addressBookEntryList
                    .add(addressBookEntry(a -> a.consensusTimestamp(startConsensusTimestamp).memo("0.0." + nodeId)
                            .nodeId(nodeId).nodeAccountId(EntityId.of("0.0." + nodeId, EntityTypeEnum.ACCOUNT))));
        }

        AddressBook.AddressBookBuilder builder = AddressBook.builder()
                .startConsensusTimestamp(startConsensusTimestamp)
                .fileData("address book memo".getBytes())
                .nodeCount(nodeCount)
                .fileId(addressBookEntityId102)
                .entries(addressBookEntryList);

        if (addressBookCustomizer != null) {
            addressBookCustomizer.accept(builder);
        }

        return builder.build();
    }

    private AddressBookEntry addressBookEntry(Consumer<AddressBookEntry.AddressBookEntryBuilder> nodeAddressCustomizer) {
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(Instant.now().getEpochSecond())
                .publicKey("rsa+public/key")
                .memo("0.0.3")
                .nodeAccountId(EntityId.of("0.0.5", EntityTypeEnum.ACCOUNT))
                .nodeId(5L)
                .nodeCertHash("nodeCertHash".getBytes());

        if (nodeAddressCustomizer != null) {
            nodeAddressCustomizer.accept(builder);
        }

        return builder.build();
    }

    private void addressBooksMatch(AddressBook expected, AddressBook actual) {
        assertAll(
                () -> assertNotNull(actual),
                () -> assertArrayEquals(expected.getFileData(), actual.getFileData()),
                () -> assertEquals(expected.getStartConsensusTimestamp(), actual.getStartConsensusTimestamp()),
                () -> assertEquals(expected.getNodeCount(), actual.getNodeCount()),
                () -> assertEquals(expected.getEntries().size(), actual.getEntries().size())
        );
    }
}
