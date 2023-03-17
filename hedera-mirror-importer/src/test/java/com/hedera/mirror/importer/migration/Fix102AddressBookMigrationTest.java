package com.hedera.mirror.importer.migration;

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

import java.io.File;
import java.io.IOException;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.repository.AddressBookRepository;

@EnabledIfV1
@Tag("migration")
class Fix102AddressBookMigrationTest extends IntegrationTest {

    @Resource
    private AddressBookRepository addressBookRepository;

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.31.0__fix_address_book_102.sql")
    private File sql;

    @Test
    void latest102WithNonNullTimestamp() throws IOException {
        AddressBook addressBook1 = addressBook(102, 1, 100L);
        AddressBook addressBook2 = addressBook(101, 101, null);
        AddressBook addressBook3 = addressBook(101, 201, null);

        runMigration();

        assertEndTimestamp(addressBook1, null);
        assertEndTimestamp(addressBook2, 200L);
        assertEndTimestamp(addressBook3, null);
    }

    @Test
    void previous102WithIncorrectTimestamp() throws IOException {
        AddressBook addressBook1 = addressBook(102, 1, 200L);
        AddressBook addressBook2 = addressBook(102, 101, 300L);
        AddressBook addressBook3 = addressBook(101, 201, null);
        AddressBook addressBook4 = addressBook(101, 301, null);

        runMigration();

        assertEndTimestamp(addressBook1, 100L);
        assertEndTimestamp(addressBook2, null);
        assertEndTimestamp(addressBook3, 300L);
        assertEndTimestamp(addressBook4, null);
    }

    @Test
    void noChanges() throws IOException {
        AddressBook addressBook1 = addressBook(102, 1, 100L);
        AddressBook addressBook2 = addressBook(102, 101, null);
        AddressBook addressBook3 = addressBook(101, 201, 300L);
        AddressBook addressBook4 = addressBook(101, 301, null);

        runMigration();

        assertEndTimestamp(addressBook1, 100L);
        assertEndTimestamp(addressBook2, null);
        assertEndTimestamp(addressBook3, 300L);
        assertEndTimestamp(addressBook4, null);
    }

    private AddressBook addressBook(long fileId, long startConsensusTimestamp, Long endConsensusTimestamp) {
        AddressBook addressBook = new AddressBook();
        addressBook.setEndConsensusTimestamp(endConsensusTimestamp);
        addressBook.setFileData(new byte[] {});
        addressBook.setFileId(EntityId.of(0, 0, fileId, EntityType.FILE));
        addressBook.setStartConsensusTimestamp(startConsensusTimestamp);
        return addressBookRepository.save(addressBook);
    }

    private void runMigration() throws IOException {
        jdbcOperations.update(FileUtils.readFileToString(sql, "UTF-8"));
    }

    private void assertEndTimestamp(AddressBook addressBook, Long endConsensusTimestamp) {
        assertThat(addressBookRepository.findById(addressBook.getStartConsensusTimestamp()))
                .get()
                .extracting(AddressBook::getEndConsensusTimestamp)
                .isEqualTo(endConsensusTimestamp);
    }
}
