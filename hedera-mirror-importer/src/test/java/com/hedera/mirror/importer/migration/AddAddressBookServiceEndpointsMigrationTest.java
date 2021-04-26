package com.hedera.mirror.importer.migration;

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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.AddressBook;
import com.hedera.mirror.importer.domain.AddressBookEntry;
import com.hedera.mirror.importer.domain.AddressBookServiceEndpoint;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.repository.AddressBookEntryRepository;
import com.hedera.mirror.importer.repository.AddressBookServiceEndpointRepository;

@Tag("migration")
@Tag("v1")
@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, statements = {"truncate table address_book restart " +
        "identity cascade;"})
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, statements = {"truncate table address_book restart " +
        "identity cascade;"})
@TestPropertySource(properties = "spring.flyway.target=1.37.0")
class AddAddressBookServiceEndpointsMigrationTest extends IntegrationTest {

    private final String baseIp = "127.0.0.";
    private final int basePort = 443;

    @Resource
    private AddressBookEntryRepository addressBookEntryRepository;

    @Resource
    private AddressBookServiceEndpointRepository addressBookServiceEndpointRepository;

    @Resource
    private JdbcOperations jdbcOperations;

    @Value("classpath:db/migration/v1/V1.37.1__add_address_book_service_endpoints.sql")
    private File sql;

    @BeforeEach
    void before() {
        revertToPreV_1_37();
    }

    @Test
    void extractServiceEndpointsForFile101() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 3;
        int numEndPoints = nodeIdCount * endPointPerNode;

        insertAddressBook(101, consensusTimestamp, nodeIdCount);
        List<AddressBookEntry> addressBookEntryList = getAndSaveAddressBookEntries(true, consensusTimestamp, 3,
                endPointPerNode);

        assertThat(addressBookEntryRepository.count()).isEqualTo(numEndPoints);

        runMigration();

        assertThat(addressBookServiceEndpointRepository
                .findAll())
                .isNotEmpty()
                .hasSize(numEndPoints)
                .extracting(AddressBookServiceEndpoint::getPort)
                .containsSequence(444, 445, 446, 447, 448, 449, 450, 451, 452);
    }

    @Test
    void extractServiceEndpointsForFile102() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 3;

        insertAddressBook(102, consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(false, consensusTimestamp, 3,
                endPointPerNode);

        assertThat(addressBookEntryRepository.count()).isEqualTo(nodeIdCount);

        runMigration();

        assertThat(addressBookServiceEndpointRepository
                .findAll())
                .isEmpty();
    }

    private List<AddressBookEntry> getAndSaveAddressBookEntries(boolean file101, long consensusTimestamp, int nodeCount,
                                                                int endPointCount) {
        List<AddressBookEntry> addressBookEntries = new ArrayList<>();
        for (int id = 1; id <= endPointCount * nodeCount; id += nodeCount) {
            addressBookEntries
                    .addAll(getAndSaveAddressBookEntry(file101, id, consensusTimestamp, 3 + id, endPointCount));
        }

        return addressBookEntries;
    }

    private List<AddressBookEntry> getAndSaveAddressBookEntry(boolean file101, long id, long consensusTimestamp,
                                                              long nodeId, int endPointCount) {
        String accountId = "0.0." + nodeId;
        List<AddressBookEntry> addressBookEntries = new ArrayList<>();
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .memo(accountId)
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeId(nodeId)
                .publicKey("rsa+public/key");

        if (file101) {
            builder = builder.nodeAccountId(EntityId.of(accountId, EntityTypeEnum.ACCOUNT));
        } else {
            // if file 102 create a single node entry
            AddressBookEntry addressBookEntry = builder.id(id).build();
            insertAddressBookEntry(addressBookEntry, "", 0);
            return addressBookEntries;
        }

        AtomicLong idCount = new AtomicLong(id);
        for (int i = 0; i < endPointCount; i++) {
            AddressBookEntry addressBookEntry = builder.id(idCount.get()).build();
            addressBookEntries.add(addressBookEntry);
            insertAddressBookEntry(addressBookEntry, baseIp + idCount.get(), basePort + (int) idCount.get());
            idCount.getAndIncrement();
        }

        return addressBookEntries;
    }

    /**
     * Insert address book object using only columns supported before V_1_37.1
     *
     * @param fileId
     * @param startConsensusTimestamp
     * @param nodeCount
     */
    private void insertAddressBook(long fileId, long startConsensusTimestamp, int nodeCount) {
        AddressBook addressBook = new AddressBook();
        addressBook.setFileData(new byte[] {});
        addressBook.setFileId(EntityId.of(0, 0, fileId, EntityTypeEnum.FILE));
        addressBook.setStartConsensusTimestamp(startConsensusTimestamp);
        addressBook.setNodeCount(nodeCount);

        jdbcOperations
                .update("insert into address_book (start_consensus_timestamp, end_consensus_timestamp, file_id, " +
                                "node_count, file_data) values" +
                                " (?, ?, ?, ?, ?)",
                        addressBook.getStartConsensusTimestamp(),
                        addressBook.getEndConsensusTimestamp(),
                        addressBook.getFileId().getId(),
                        addressBook.getNodeCount(),
                        addressBook.getFileData());
    }

    /**
     * Insert address book entry object using only columns supported before V_1_37.1
     *
     * @param addressBookEntry address book entry ref
     * @param ip               service endpoint ip
     * @param port             service endpoint  port
     */
    private void insertAddressBookEntry(AddressBookEntry addressBookEntry, String ip, int port) {
        Long nodeAccountId = addressBookEntry.getNodeAccountId() == null ? null : addressBookEntry.getNodeAccountId()
                .getId();
        jdbcOperations
                .update("insert into address_book_entry (id, consensus_timestamp, ip, memo, node_account_id, " +
                                "node_cert_hash, node_id, port, public_key) values" +
                                " (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        addressBookEntry.getId(),
                        addressBookEntry.getConsensusTimestamp(),
                        ip,
                        addressBookEntry.getMemo(),
                        nodeAccountId,
                        addressBookEntry.getNodeCertHash(),
                        addressBookEntry.getNodeId(),
                        port,
                        addressBookEntry.getPublicKey());
    }

    private void runMigration() throws IOException {
        log.info("Run migration: {}", sql.getName());
        jdbcOperations.update(FileUtils.readFileToString(sql, "UTF-8"));
    }

    /**
     * Ensure entity tables match expected state before V_1_37.1
     */
    private void revertToPreV_1_37() {
        // remove address_book_service_endpoint table if present
        jdbcOperations
                .execute("drop table if exists address_book_service_endpoint cascade;");

        // drop describe and stake columns
        jdbcOperations
                .execute("alter table if exists address_book_entry\n" +
                        "    drop column if exists description,\n" +
                        "    drop column if exists stake;");

        // restore ip and port columns
        jdbcOperations
                .execute("alter table if exists address_book_entry\n" +
                        "    add column if not exists ip varchar(128) null,\n" +
                        "    add column if not exists port integer null;");
    }
}
