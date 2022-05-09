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
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class FillMissingContractInitsourceMigrationTest extends IntegrationTest {

    private static final String TABLE_IDS = "id";

    private static final String TABLE_NAME = "contract";

    private final RecursiveComparisonConfiguration contractComparisonConfig = RecursiveComparisonConfiguration
            .builder()
            .withComparedFields("createdTimestamp", "fileId", "id", "initcode", "num", "realm", "shard", "timestampRange")
            .withIgnoredFields("fileId.type")
            .build();

    @Value("classpath:db/migration/v1/R__fill_missing_contract_initsource.sql")
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
        expected.add(domainBuilder.contract().customize(c -> c.fileId(null).createdTimestamp(null)
                .timestampRange(Range.atLeast(0L))).persist());

        // pre 0.23, child contract created in a contractcall transaction. Note the child contract has a history row
        var parent = domainBuilder.contract().persist();
        var createdTimestamp = domainBuilder.timestamp();
        var updatedTimestamp = createdTimestamp + 20L;
        var child = domainBuilder.contract().customize(c -> c.fileId(null).createdTimestamp(createdTimestamp)
                .timestampRange(Range.atLeast(updatedTimestamp))).persist();
        // history row for child
        var history = TestUtils.clone(child);
        history.setTimestampRange(Range.closedOpen(createdTimestamp, updatedTimestamp));
        insertContractHistory(history);
        insertContractResult(createdTimestamp, parent, List.of(child.getId()));
        child.setFileId(parent.getFileId());
        expected.addAll(List.of(parent, child));
        var expectedHistory = TestUtils.clone(history);
        expectedHistory.setFileId(parent.getFileId());

        // pre 0.23, grandchild of parent
        var grandchild = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(grandchild.getCreatedTimestamp(), child, List.of(grandchild.getId()));
        grandchild.setFileId(parent.getFileId());
        expected.add(grandchild);

        // pre 0.23, parent is also missing fileId
        parent = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(child.getCreatedTimestamp(), parent, List.of(child.getId()));
        expected.addAll(List.of(parent, child));

        // post 0.23, child contract in its own contractcreate transaction
        parent = domainBuilder.contract().persist();
        child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(parent.getCreatedTimestamp(), parent, List.of(parent.getId(), child.getId()));
        insertContractResult(child.getCreatedTimestamp(), child, Collections.emptyList());
        child.setFileId(parent.getFileId());
        expected.addAll(List.of(parent, child));

        // post 0.26, child contract in its own contractcreate transaction, parent is created with initcode
        parent = domainBuilder.contract().customize(c -> c.fileId(null).initcode(domainBuilder.bytes(32))).persist();
        child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(parent.getCreatedTimestamp(), parent, List.of(parent.getId(), child.getId()));
        insertContractResult(child.getCreatedTimestamp(), child, Collections.emptyList());
        child.setInitcode(parent.getInitcode());
        expected.addAll(List.of(parent, child));

        migrate();

        assertThat(findEntity(Contract.class, TABLE_IDS, TABLE_NAME))
                .usingRecursiveFieldByFieldElementComparator(contractComparisonConfig)
                .containsExactlyInAnyOrderElementsOf(expected);
        assertThat(findHistory(Contract.class, TABLE_IDS, TABLE_NAME))
                .usingRecursiveFieldByFieldElementComparator(contractComparisonConfig)
                .containsOnly(expectedHistory);
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

    private void insertContractResult(long consensusTimestamp, Contract caller, List<Long> createdContractIds) {
        domainBuilder.contractResult()
                .customize(cr -> cr.consensusTimestamp(consensusTimestamp)
                        .contractId(caller.toEntityId())
                        .createdContractIds(createdContractIds))
                .persist();
    }
}
