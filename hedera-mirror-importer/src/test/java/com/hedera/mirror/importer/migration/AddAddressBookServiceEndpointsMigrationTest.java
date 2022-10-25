package com.hedera.mirror.importer.migration;

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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.domain.addressbook.AddressBook;
import com.hedera.mirror.common.domain.addressbook.AddressBookEntry;
import com.hedera.mirror.common.domain.addressbook.AddressBookServiceEndpoint;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.addressbook.AddressBookServiceImpl;
import com.hedera.mirror.importer.config.Owner;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.37.0")
class AddAddressBookServiceEndpointsMigrationTest extends IntegrationTest {

    private final String baseAccountId = "0.0.";
    private final String baseIp = "127.0.0.";
    private final int basePort = 443;
    private final int nodeAccountOffset = 3;
    private final @Owner JdbcOperations jdbcOperations;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    @Value("classpath:db/migration/v1/V1.37.1__add_address_book_service_endpoints.sql")
    private final File sql;

    private int addressBookEntryIdCounter;

    @BeforeEach
    void before() {
        revertToPreV_1_37();
        jdbcOperations
                .execute("alter table address_book_entry drop constraint if exists " +
                        "address_book_entry_consensus_timestamp_fkey");
        // previous address_book_entry had
        addressBookEntryIdCounter = 1;
    }

    @Test
    void verifyMigrateWhenBothDeprecatedIpAndAddressBookServiceEndpointsAreSet() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 3;
        int numEndPoints = nodeIdCount * (endPointPerNode + 1);

        insertAddressBook(AddressBookServiceImpl.FILE_101, consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(true, consensusTimestamp, nodeIdCount, endPointPerNode);

        assertThat(findAddressBookEntries().stream().count()).isEqualTo(numEndPoints);

        runMigration();

        assertThat(findAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookEntry::getId)
                .extracting(MigrationAddressBookEntry.Id::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L);

        assertThat(findAddressBookServiceEndpoints())
                .isNotEmpty()
                .hasSize(numEndPoints)
                .extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getPort)
                .containsExactlyInAnyOrder(443, 444, 445, 446, 447, 448, 449, 450, 451, 452, 453, 454);
    }

    @Test
    void verifyMigrateWhenOnlyAddressBookServiceEndpointsAreSet() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 3;
        int numEndPoints = nodeIdCount * endPointPerNode;

        insertAddressBook(AddressBookServiceImpl.FILE_102, consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(false, consensusTimestamp, nodeIdCount,
                endPointPerNode);

        assertThat(findAddressBookEntries().stream().count()).isEqualTo(numEndPoints);

        runMigration();

        assertThat(findAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookEntry::getId)
                .extracting(MigrationAddressBookEntry.Id::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L);

        assertThat(findAddressBookServiceEndpoints())
                .isNotEmpty()
                .hasSize(numEndPoints)
                .extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getPort)
                .containsExactlyInAnyOrder(443, 444, 445, 446, 447, 448, 449, 450, 451);
    }

    @Test
    void verifyMigrateWhenOnlyDeprecatedIpIsSet() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 0;
        int numEndPoints = nodeIdCount;

        insertAddressBook(AddressBookServiceImpl.FILE_102, consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(true, consensusTimestamp, nodeIdCount,
                endPointPerNode);

        assertThat(findAddressBookEntries().stream().count()).isEqualTo(numEndPoints);

        runMigration();

        assertThat(findAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookEntry::getId)
                .extracting(MigrationAddressBookEntry.Id::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L);

        assertThat(findAddressBookServiceEndpoints())
                .isNotEmpty()
                .hasSize(numEndPoints)
                .extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getPort)
                .containsExactlyInAnyOrder(443, 444, 445);
    }

    @Test
    void verifyMigrateWhenNeitherDeprecatedIpNorAddressBookServiceEndpointsAreSet() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;
        int endPointPerNode = 0;

        insertAddressBook(AddressBookServiceImpl.FILE_102, consensusTimestamp, nodeIdCount);
        getAndSaveAddressBookEntries(false, consensusTimestamp, nodeIdCount,
                endPointPerNode);

        assertThat(findAddressBookEntries().stream().count()).isEqualTo(nodeIdCount);

        runMigration();

        assertThat(findAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIdCount)
                .extracting(MigrationAddressBookEntry::getId)
                .extracting(MigrationAddressBookEntry.Id::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L);

        assertThat(findAddressBookServiceEndpoints())
                .isEmpty();
    }

    @Test
    void verifyAddressBookEntryDuplicatesRemoved() throws IOException {
        long consensusTimestamp = 1;
        int nodeIdCount = 3;

        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeId(0L)
                .publicKey("rsa+public/key");

        List<Long> nodeIds = List.of(0L, 1L, 2L);
        List<Integer> ports = List.of(80, 443, 5600);
        int numEndPoints = nodeIds.size() * ports.size();

        // populate address_book and address_book_entry
        insertAddressBook(AddressBookServiceImpl.FILE_102, consensusTimestamp, nodeIdCount);
        nodeIds.forEach(nodeId -> {
            ports.forEach(port -> {
                insertAddressBookEntry(
                        builder.memo(baseAccountId + (nodeId + nodeAccountOffset)).build(),
                        baseIp + nodeId, // duplicate ip per nodeId
                        port);
            });
        });

        assertThat(findAddressBookEntries().stream().count()).isEqualTo(numEndPoints);

        runMigration();

        assertThat(findAddressBookEntries())
                .isNotEmpty()
                .hasSize(nodeIds.size())
                .extracting(MigrationAddressBookEntry::getId)
                .extracting(MigrationAddressBookEntry.Id::getNodeId)
                .containsExactlyInAnyOrderElementsOf(nodeIds);

        ListAssert<AddressBookServiceEndpoint> listAssert = assertThat(findAddressBookServiceEndpoints())
                .isNotEmpty()
                .hasSize(numEndPoints);

        List<Integer> allPorts = new ArrayList<>();
        allPorts.addAll(ports);
        allPorts.addAll(ports);
        allPorts.addAll(ports);
        listAssert
                .extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getPort)
                .containsExactlyInAnyOrderElementsOf(allPorts);

        // verify address_book counts are updated
        assertThat(findAddressBook(consensusTimestamp))
                .get()
                .returns(AddressBookServiceImpl.FILE_102, MigrationAddressBook::getFileId)
                .returns(nodeIds.size(), MigrationAddressBook::getNodeCount)
                .returns(0L, MigrationAddressBook::getEndConsensusTimestamp);
    }

    @Test
    void verifyInitialAddressBookNullEntriesUpdated() throws IOException {
        long consensusTimestamp = 1;
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeId(0L)
                .publicKey("rsa+public/key");

        List<Long> nodeIds = List.of(0L, 1L, 2L, 3L);
        insertAddressBook(AddressBookServiceImpl.FILE_102, consensusTimestamp, nodeIds.size());
        insertAddressBookEntry(builder.memo(baseAccountId + (nodeIds.get(0) + nodeAccountOffset)).build(), "", 0);
        insertAddressBookEntry(builder.memo(baseAccountId + (nodeIds.get(1) + nodeAccountOffset)).build(), "", 0);
        insertAddressBookEntry(builder.memo(baseAccountId + (nodeIds.get(2) + nodeAccountOffset)).build(), "", 0);
        insertAddressBookEntry(builder.memo(baseAccountId + (nodeIds.get(3) + nodeAccountOffset)).build(), "", 0);

        runMigration();

        ListAssert<MigrationAddressBookEntry> listAssert =
                assertThat(findAddressBookEntries())
                        .isNotEmpty()
                        .hasSize(nodeIds.size());

        listAssert.extracting(MigrationAddressBookEntry::getId).extracting(MigrationAddressBookEntry.Id::getNodeId)
                .containsExactlyInAnyOrderElementsOf(nodeIds);
        listAssert.extracting(MigrationAddressBookEntry::getNodeAccountId)
                .containsExactlyInAnyOrder(
                        EntityId.of(baseAccountId + (nodeIds.get(0) + nodeAccountOffset), EntityType.ACCOUNT),
                        EntityId.of(baseAccountId + (nodeIds.get(1) + nodeAccountOffset), EntityType.ACCOUNT),
                        EntityId.of(baseAccountId + (nodeIds.get(2) + nodeAccountOffset), EntityType.ACCOUNT),
                        EntityId.of(baseAccountId + (nodeIds.get(3) + nodeAccountOffset), EntityType.ACCOUNT));

        assertThat(findAddressBookServiceEndpoints())
                .isEmpty();
    }

    @Test
    void verifyAddressBookWithValidIpAndInvalidPortMigration() throws IOException {
        long consensusTimestamp = 1;
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .nodeCertHash("nodeCertHash".getBytes())
                .publicKey("rsa+public/key");

        List<Long> nodeIds = List.of(0L, 1L, 2L, 3L);
        insertAddressBook(AddressBookServiceImpl.FILE_102, consensusTimestamp, nodeIds.size());
        insertAddressBookEntry(
                builder.memo(baseAccountId + (nodeIds.get(0) + nodeAccountOffset))
                        .consensusTimestamp(consensusTimestamp)
                        .nodeId(nodeIds.get(0))
                        .build(), "127.0.0.1", null);
        insertAddressBookEntry(builder.memo(baseAccountId + (nodeIds.get(1) + nodeAccountOffset))
                .consensusTimestamp(consensusTimestamp)
                .nodeId(nodeIds.get(1))
                .build(), "127.0.0.2", 0);
        insertAddressBookEntry(builder.memo(baseAccountId + (nodeIds.get(2) + nodeAccountOffset))
                .consensusTimestamp(consensusTimestamp)
                .nodeId(nodeIds.get(2))
                .build(), "127.0.0.3", null);
        insertAddressBookEntry(builder.memo(baseAccountId + (nodeIds.get(3) + nodeAccountOffset))
                .consensusTimestamp(consensusTimestamp)
                .nodeId(nodeIds.get(3))
                .build(), "127.0.0.4", 50211);

        runMigration();

        ListAssert<MigrationAddressBookEntry> listAssert =
                assertThat(findAddressBookEntries())
                        .isNotEmpty()
                        .hasSize(nodeIds.size());

        listAssert.extracting(MigrationAddressBookEntry::getId).extracting(MigrationAddressBookEntry.Id::getNodeId)
                .containsExactlyInAnyOrderElementsOf(nodeIds);
        listAssert.extracting(MigrationAddressBookEntry::getNodeAccountId)
                .containsExactlyInAnyOrder(
                        EntityId.of(baseAccountId + (nodeIds.get(0) + nodeAccountOffset), EntityType.ACCOUNT),
                        EntityId.of(baseAccountId + (nodeIds.get(1) + nodeAccountOffset), EntityType.ACCOUNT),
                        EntityId.of(baseAccountId + (nodeIds.get(2) + nodeAccountOffset), EntityType.ACCOUNT),
                        EntityId.of(baseAccountId + (nodeIds.get(3) + nodeAccountOffset), EntityType.ACCOUNT));

        ListAssert<AddressBookServiceEndpoint> serviceListAssert =
                assertThat(findAddressBookServiceEndpoints())
                        .isNotEmpty()
                        .hasSize(nodeIds.size());

        serviceListAssert.extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getNodeId)
                .containsExactlyInAnyOrder(0L, 1L, 2L, 3L);

        serviceListAssert.extracting(AddressBookServiceEndpoint::getId)
                .extracting(AddressBookServiceEndpoint.Id::getPort)
                .containsExactlyInAnyOrder(-1, 0, -1, 50211);
    }

    private List<AddressBookEntry> getAndSaveAddressBookEntries(boolean deprecatedIp, long consensusTimestamp,
                                                                int nodeCount, int endPointCount) {
        List<AddressBookEntry> addressBookEntries = new ArrayList<>();
        int numEndpointsPerNode = deprecatedIp ? endPointCount + 1 : endPointCount;
        for (int id = 0; id < nodeCount; id++) {
            addressBookEntries.addAll(getAndSaveAddressBookEntry(
                    deprecatedIp,
                    (long) numEndpointsPerNode * id,
                    consensusTimestamp,
                    id,
                    endPointCount));
        }

        return addressBookEntries;
    }

    private List<AddressBookEntry> getAndSaveAddressBookEntry(boolean deprecatedIp, long id, long consensusTimestamp,
                                                              long nodeId, int endPointCount) {
        long accountIdNum = nodeAccountOffset + nodeId;
        String accountId = baseAccountId + accountIdNum;
        List<AddressBookEntry> addressBookEntries = new ArrayList<>();
        AddressBookEntry.AddressBookEntryBuilder builder = AddressBookEntry.builder()
                .consensusTimestamp(consensusTimestamp)
                .nodeId(nodeId)
                .memo(accountId)
                .nodeCertHash("nodeCertHash".getBytes())
                .nodeAccountId(EntityId.of(accountId, EntityType.ACCOUNT))
                .publicKey("rsa+public/key");

        AtomicLong idCount = new AtomicLong(id);
        AddressBookEntry addressBookEntry = builder.build();
        if (deprecatedIp) {
            insertAddressBookEntry(addressBookEntry, baseIp + idCount.get(), basePort + (int) idCount.get());
            idCount.getAndIncrement();
            addressBookEntries.add(addressBookEntry);
        }

        for (int i = 1; i <= endPointCount; i++) {
            insertAddressBookEntry(addressBookEntry, baseIp + idCount.get(), basePort + (int) idCount.get());
            idCount.getAndIncrement();
            addressBookEntries.add(addressBookEntry);
        }

        // handle no endpoints case
        if (!deprecatedIp && endPointCount == 0) {
            insertAddressBookEntry(addressBookEntry, "", 0);
            addressBookEntries.add(addressBookEntry);
        }

        return addressBookEntries;
    }

    private List<AddressBookServiceEndpoint> findAddressBookServiceEndpoints() {
        return jdbcOperations.query("select * from address_book_service_endpoint",
                (rs, rowNum) ->
                        AddressBookServiceEndpoint.builder()
                                .consensusTimestamp(rs.getLong("consensus_timestamp"))
                                .ipAddressV4(rs.getString("ip_address_v4"))
                                .nodeId(rs.getLong("node_id"))
                                .port(rs.getInt("port"))
                                .build()
        );
    }

    private Optional<MigrationAddressBook> findAddressBook(long consensusTimestamp) {
        return jdbcTemplate.query("select * from address_book where start_consensus_timestamp <= :consensusTimestamp " +
                        "order by start_consensus_timestamp desc limit 1",
                Map.of("consensusTimestamp", consensusTimestamp), (rs, rowNum) ->
                        MigrationAddressBook.builder()
                                .startConsensusTimestamp(rs.getLong("start_consensus_timestamp"))
                                .endConsensusTimestamp(rs.getLong("end_consensus_timestamp"))
                                .fileData(rs.getBytes("file_data"))
                                .fileId(EntityId.of(rs.getLong("file_id"), EntityType.FILE))
                                .nodeCount(rs.getInt("node_count"))
                                .build()
        ).stream().findFirst();
    }

    private List<MigrationAddressBookEntry> findAddressBookEntries() {
        return jdbcOperations.query("select * from address_book_entry",
                (rs, rowNum) ->
                        MigrationAddressBookEntry.builder()
                                .consensusTimestamp(rs.getLong("consensus_timestamp"))
                                .nodeId(rs.getLong("node_id"))
                                .memo(rs.getString("memo"))
                                .nodeCertHash(rs.getBytes("node_cert_hash"))
                                .nodeAccountId(EntityId.of(rs.getLong("node_account_id"), EntityType.ACCOUNT))
                                .publicKey(rs.getBytes("public_key")).build()
        );
    }

    /**
     * Insert address book object using only columns supported before V_1_37.1
     *
     * @param fileEntityId
     * @param startConsensusTimestamp
     * @param nodeCount
     */
    private void insertAddressBook(EntityId fileEntityId, long startConsensusTimestamp, int nodeCount) {
        AddressBook addressBook = new AddressBook();
        addressBook.setFileData(new byte[] {});
        addressBook.setFileId(fileEntityId);
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
    private void insertAddressBookEntry(AddressBookEntry addressBookEntry, String ip, Integer port) {
        Long nodeAccountId = EntityId.isEmpty(addressBookEntry.getNodeAccountId()) ? null :
                addressBookEntry.getNodeAccountId().getId();
        jdbcOperations
                .update("insert into address_book_entry (id, consensus_timestamp, ip, memo, node_account_id, " +
                                "node_cert_hash, node_id, port, public_key) values" +
                                " (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        addressBookEntryIdCounter++,
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

        // drop describe and stake columns. Also drop primary key
        jdbcOperations
                .execute("alter table if exists address_book_entry\n" +
                        "    drop column if exists description,\n" +
                        "    drop column if exists stake,\n" +
                        "drop constraint if exists address_book_entry_pkey;");

        // restore id, ip and port columns. Also restore primary key
        jdbcOperations
                .execute("alter table if exists address_book_entry\n" +
                        "    add column if not exists id integer,\n" +
                        "    add column if not exists ip varchar(128) null,\n" +
                        "    add column if not exists port integer null,\n" +
                        "alter column node_account_id drop not null,\n" +
                        "alter column node_id drop not null,\n" +
                        "add primary key (id);");
    }

    @Builder(toBuilder = true)
    @Data
    private static class MigrationAddressBook {
        private long startConsensusTimestamp;
        private long endConsensusTimestamp;
        private List<MigrationAddressBookEntry> entries;
        private byte[] fileData;
        private EntityId fileId;
        private int nodeCount;
    }

    @Builder(toBuilder = true)
    @Data
    private static class MigrationAddressBookEntry {
        private final long consensusTimestamp;
        private final MigrationAddressBookEntry.Id id;
        private final String memo;
        private final byte[] nodeCertHash;
        private final EntityId nodeAccountId;
        private final long nodeId;
        private final int port;
        private final byte[] publicKey;

        public MigrationAddressBookEntry.Id getId() {
            MigrationAddressBookEntry.Id id = new MigrationAddressBookEntry.Id();
            id.setConsensusTimestamp(consensusTimestamp);
            id.setNodeId(nodeId);
            return id;
        }

        @Data
        public static class Id implements Serializable {
            private static final long serialVersionUID = -3761184325551298389L;
            private Long consensusTimestamp;
            private Long nodeId;
        }
    }
}
