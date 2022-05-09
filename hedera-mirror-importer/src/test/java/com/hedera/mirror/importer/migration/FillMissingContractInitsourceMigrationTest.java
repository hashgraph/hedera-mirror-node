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

import com.google.common.collect.Range;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.File;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.SuperBuilder;
import org.apache.commons.io.FileUtils;
import org.hibernate.annotations.Type;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.History;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;

@EnabledIfV1
@EntityScan
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
@TestPropertySource(properties = "spring.flyway.target=1.59.0")
class FillMissingContractInitsourceMigrationTest extends IntegrationTest {

    private static final String TABLE_IDS = "id";

    private static final String TABLE_NAME = "contract";

    @Value("classpath:db/migration/v1/V1.59.1__fill_missing_contract_initsource.sql")
    private File migrationSql;

    @Test
    void empty() {
        migrate();
        assertThat(findEntity(Contract.class, TABLE_IDS, TABLE_NAME)).isEmpty();
        assertThat(findHistory(Contract.class, TABLE_IDS, TABLE_NAME)).isEmpty();
    }

    @Test
    void fillMissingContractInitsource() {
        List<Contract> expected = new LinkedList<>();
        // there are contract entities with just shard.realm.num and [0,) timestamp range
        expected.add(contract().customize(c -> c.createdTimestamp(null).fileId(null)
                .timestampRange(Range.atLeast(0L))).persist());

        // pre 0.23, child contract created in a contractcall transaction. Note the child contract has a history row
        var parent = contract().persist();
        var child = contract()
                .customize(c -> c.fileId(null).timestampRange(Range.atLeast(c.createdTimestamp + 20L))).persist();
        // history row for child
        var history = TestUtils.clone(child);
        history.setTimestampRange(Range.closedOpen(child.getCreatedTimestamp(), child.getCreatedTimestamp() + 20L));
        insertContractHistory(history);
        contractResult(child.getCreatedTimestamp(), parent.getId(), List.of(child.getId())).persist();
        child.setFileId(parent.getFileId());
        expected.addAll(List.of(parent, child));
        var expectedHistory = TestUtils.clone(history);
        expectedHistory.setFileId(parent.getFileId());

        // pre 0.23, grandchild of parent
        var grandchild = contract().customize(c -> c.fileId(null)).persist();
        contractResult(grandchild.getCreatedTimestamp(), child.getId(), List.of(grandchild.getId())).persist();
        grandchild.setFileId(parent.getFileId());
        expected.add(grandchild);

        // pre 0.23, parent is also missing fileId
        var parent3 = contract().customize(c -> c.fileId(null)).persist();
        child = contract().customize(c -> c.fileId(null)).persist();
        contractResult(child.getCreatedTimestamp(), parent3.getId(), List.of(child.getId())).persist();
        expected.addAll(List.of(parent3, child));

        // post 0.23, child contract in its own contractcreate transaction
        var parent4 = contract().persist();
        child = contract().customize(c -> c.fileId(null)).persist();
        contractResult(parent4.getCreatedTimestamp(), parent4.getId(), List.of(parent4.getId(), child.getId())).persist();
        contractResult(child.getCreatedTimestamp(), child.getId(), Collections.emptyList()).persist();
        child.setFileId(parent4.getFileId());
        expected.addAll(List.of(parent4, child));

        // post 0.26, child contract in its own contractcreate transaction, parent is created with initcode
        var parent5 = contract().customize(c -> c.fileId(null).initcode(domainBuilder.bytes(32))).persist();
        child = contract().customize(c -> c.fileId(null)).persist();
        contractResult(parent5.getCreatedTimestamp(), parent5.getId(), List.of(parent5.getId(), child.getId())).persist();
        contractResult(child.getCreatedTimestamp(), child.getId(), Collections.emptyList()).persist();
        child.setInitcode(parent5.getInitcode());
        expected.addAll(List.of(parent5, child));

        migrate();

        assertThat(findEntity(Contract.class, TABLE_IDS, TABLE_NAME)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(findHistory(Contract.class, TABLE_IDS, TABLE_NAME)).containsOnly(expectedHistory);
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private void insertContractHistory(Contract contract) {
        String sql = "insert into contract_history (created_timestamp, file_id, id, initcode, num, realm, shard, " +
                "timestamp_range) values (?, ?, ?, ?, ?, ?, ?, ?::int8range)";
        jdbcOperations.update(sql, contract.getCreatedTimestamp(), contract.getFileId(), contract.getId(),
                contract.getInitcode(), contract.getNum(), contract.getRealm(), contract.getShard(),
                PostgreSQLGuavaRangeType.INSTANCE.asString(contract.getTimestampRange()));
    }

    private DomainWrapper<Contract, Contract.ContractBuilder> contract() {
        var createdTimestamp = domainBuilder.timestamp();
        var contractId = domainBuilder.id();
        var builder = Contract.builder()
                .createdTimestamp(createdTimestamp)
                .fileId(domainBuilder.id())
                .id(contractId)
                .num(contractId)
                .timestampRange(Range.atLeast(createdTimestamp));
        return domainBuilder.wrap(builder, builder::build);
    }

    private DomainWrapper<ContractResult, ContractResult.ContractResultBuilder> contractResult(long consensusTimestamp,
                                                                                               long contractId,
                                                                                               List<Long> createdContractIds) {
        var builder = ContractResult.builder()
                .consensusTimestamp(consensusTimestamp)
                .contractId(contractId)
                .createdContractIds(createdContractIds)
                .functionParameters(domainBuilder.bytes(32))
                .gasLimit(2_000_000L)
                .payerAccountId(domainBuilder.id());
        return domainBuilder.wrap(builder, builder::build);
    }

    @Data
    @Entity
    @Table(name = TABLE_NAME)
    @NoArgsConstructor
    @SuperBuilder
    public static class Contract implements History {
        private Long createdTimestamp;
        private Long fileId;
        @Id
        private long id;
        private byte[] initcode;
        private long num;
        private long realm;
        private long shard;
        private Range<Long> timestampRange;
    }

    @Data
    @Entity
    @Table(name = "contract_result")
    @NoArgsConstructor
    @SuperBuilder
    public static class ContractResult {
        @Type(type = "com.vladmihalcea.hibernate.type.array.ListArrayType")
        private List<Long> createdContractIds = Collections.emptyList();
        @Id
        private long consensusTimestamp;
        private long contractId;
        private byte[] functionParameters;
        private long gasLimit;
        private long payerAccountId;
    }
}
