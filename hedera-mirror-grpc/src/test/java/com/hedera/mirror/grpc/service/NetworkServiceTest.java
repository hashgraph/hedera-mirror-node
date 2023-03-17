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

import static com.hedera.mirror.grpc.service.NetworkServiceImpl.INVALID_FILE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import javax.annotation.Resource;
import javax.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.grpc.GrpcIntegrationTest;
import com.hedera.mirror.grpc.domain.AddressBookFilter;
import com.hedera.mirror.grpc.exception.EntityNotFoundException;
import com.hedera.mirror.grpc.repository.AddressBookEntryRepository;

class NetworkServiceTest extends GrpcIntegrationTest {

    private static final long CONSENSUS_TIMESTAMP = 1L;

    @Resource
    private AddressBookEntryRepository addressBookEntryRepository;

    @Resource
    private AddressBookProperties addressBookProperties;

    @Autowired
    private DomainBuilder domainBuilder;

    @Resource
    private NetworkService networkService;

    private int pageSize;

    @BeforeEach
    void setup() {
        pageSize = addressBookProperties.getPageSize();
    }

    @AfterEach
    void cleanup() {
        addressBookProperties.setPageSize(pageSize);
    }

    @Test
    void invalidFilter() {
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(null)
                .limit(-1)
                .build();

        assertThatThrownBy(() -> networkService.getNodes(filter))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("limit: must be greater than or equal to 0")
                .hasMessageContaining("fileId: must not be null");
    }

    @Test
    void addressBookNotFound() {
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(EntityId.of(102L, EntityType.FILE))
                .build();

        assertThatThrownBy(() -> networkService.getNodes(filter)).isInstanceOf(EntityNotFoundException.class)
                .hasMessage("File 0.0.102 does not exist");
    }

    @Test
    void invalidAddressBookFile() {
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(EntityId.of(999L, EntityType.FILE))
                .build();

        assertThatThrownBy(() -> networkService.getNodes(filter))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(INVALID_FILE_ID);
    }

    @Test
    void noNodes() {
        AddressBook addressBook = addressBook();
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(addressBook.getFileId())
                .build();

        StepVerifier.withVirtualTime(()-> networkService.getNodes(filter))
                .thenAwait(Duration.ofSeconds(10L))
                .expectNextCount(0L)
                .expectComplete()
                .verify(Duration.ofSeconds(10L));
    }

    @Test
    void lessThanPageSize() {
        addressBookProperties.setPageSize(2);
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry = addressBookEntry();
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(addressBook.getFileId())
                .build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry);
    }

    @Test
    void equalToPageSize() {
        addressBookProperties.setPageSize(2);
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        AddressBookEntry addressBookEntry2 = addressBookEntry();
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(addressBook.getFileId())
                .build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2);
    }

    @Test
    void moreThanPageSize() {
        addressBookProperties.setPageSize(2);
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        AddressBookEntry addressBookEntry2 = addressBookEntry();
        AddressBookEntry addressBookEntry3 = addressBookEntry();
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(addressBook.getFileId())
                .build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2, addressBookEntry3);
    }

    @Test
    void limitReached() {
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        addressBookEntry();
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(addressBook.getFileId())
                .limit(1)
                .build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1);
    }

    @Test
    void cached() {
        addressBookProperties.setPageSize(2);
        AddressBook addressBook = addressBook();
        AddressBookEntry addressBookEntry1 = addressBookEntry();
        AddressBookEntry addressBookEntry2 = addressBookEntry();
        AddressBookEntry addressBookEntry3 = addressBookEntry();
        AddressBookFilter filter = AddressBookFilter.builder()
                .fileId(addressBook.getFileId())
                .build();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2, addressBookEntry3);

        addressBookEntryRepository.deleteAll();

        assertThat(getNodes(filter)).containsExactly(addressBookEntry1, addressBookEntry2, addressBookEntry3);
    }

    private List<AddressBookEntry> getNodes(AddressBookFilter filter) {
        return networkService.getNodes(filter).collectList().block(Duration.ofMillis(1000L));
    }

    private AddressBook addressBook() {
        return domainBuilder.addressBook().customize(a -> a.startConsensusTimestamp(CONSENSUS_TIMESTAMP)).persist();
    }

    private AddressBookEntry addressBookEntry() {
        return domainBuilder.addressBookEntry().customize(a -> a.consensusTimestamp(CONSENSUS_TIMESTAMP)).persist();
    }
}
