/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.repository;

import static com.hedera.mirror.importer.addressbook.AddressBookServiceImpl.FILE_101;
import static com.hedera.mirror.importer.addressbook.AddressBookServiceImpl.FILE_102;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class AddressBookRepositoryTest extends ImporterIntegrationTest {

    private final AddressBookRepository addressBookRepository;

    @Test
    void save() {
        var addressBook = domainBuilder.addressBook().get();
        var addressBookEntry = domainBuilder
                .addressBookEntry()
                .customize(e -> e.consensusTimestamp(addressBook.getStartConsensusTimestamp()))
                .get();
        addressBook.getEntries().add(addressBookEntry);
        addressBookRepository.save(addressBook);
        assertThat(addressBookRepository.findById(addressBook.getStartConsensusTimestamp()))
                .get()
                .isEqualTo(addressBook)
                .extracting(AddressBook::getEntries)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(AtomicReference.class, EntityType.class)
                .isEqualTo(addressBook.getEntries());
    }

    @Test
    void findLatest() {
        domainBuilder.addressBook().persist();
        var addressBook2 = domainBuilder.addressBook().persist();
        var addressBook3 =
                domainBuilder.addressBook().customize(a -> a.fileId(FILE_101)).persist();

        assertThat(addressBookRepository.findLatest(addressBook3.getStartConsensusTimestamp(), FILE_102.getId()))
                .get()
                .isEqualTo(addressBook2);
    }
}
