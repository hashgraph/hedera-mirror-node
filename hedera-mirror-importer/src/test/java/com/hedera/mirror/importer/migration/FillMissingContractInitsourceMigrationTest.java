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
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.EnabledIfV1;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.repository.ContractRepository;

@EnabledIfV1
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Tag("migration")
class FillMissingContractInitsourceMigrationTest extends IntegrationTest {

    @Value("classpath:db/migration/v1/R__fill_missing_contract_initsource.sql")
    private final File migrationSql;

    private final ContractRepository contractRepository;

    @Test
    void empty() {
        migrate();
        assertThat(contractRepository.findAll()).isEmpty();
    }

    @Test
    void noParent() {
        var parent = domainBuilder.contract().customize(c -> c.fileId(null)).persist();

        migrate();

        assertContracts(parent);
    }

    @DisplayName("pre 0.23, child contract created in a contract call transaction")
    @Test
    void pre023() {
        var parent = domainBuilder.contract().persist();
        var child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        var grandchild = domainBuilder.contract().customize(c -> c.fileId(null)).persist();

        insertContractResult(parent, List.of(child.getId()));
        insertContractResult(child, List.of(grandchild.getId()));
        child.setFileId(parent.getFileId());
        grandchild.setFileId(parent.getFileId());

        migrate();

        assertContracts(parent, child, grandchild);
    }

    @DisplayName("pre 0.23, parent is also missing fileId")
    @Test
    void pre023NoParentFileId() {
        var parent = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        var child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(parent, List.of(child.getId()));

        migrate();

        assertContracts(parent, child);
    }

    @DisplayName("post 0.23, child contract in its own contract create transaction")
    @Test
    void post023SyntheticContractCreate() {
        var parent = domainBuilder.contract().persist();
        var child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(parent, List.of(parent.getId(), child.getId()));
        insertContractResult(child, Collections.emptyList());
        child.setFileId(parent.getFileId());

        migrate();

        assertContracts(parent, child);
    }

    @DisplayName("post 0.26, child contract in its own contract create transaction, parent is created with initcode")
    @Test
    void post026ParentWithInitcode() {
        var parent = domainBuilder.contract().customize(c -> c.fileId(null).initcode(domainBuilder.bytes(32)))
                .persist();
        var child = domainBuilder.contract().customize(c -> c.fileId(null)).persist();
        insertContractResult(parent, List.of(parent.getId(), child.getId()));
        insertContractResult(child, Collections.emptyList());
        child.setInitcode(parent.getInitcode());

        migrate();

        assertContracts(parent, child);
    }

    @SneakyThrows
    private void migrate() {
        jdbcOperations.update(FileUtils.readFileToString(migrationSql, "UTF-8"));
    }

    private void insertContractResult(Contract caller, List<Long> createdContractIds) {
        domainBuilder.contractResult()
                .customize(cr -> cr.contractId(EntityId.of(caller.getId(), EntityType.CONTRACT))
                        .createdContractIds(createdContractIds))
                .persist();
    }

    private void assertContracts(Contract... contracts) {
        assertThat(contractRepository.findAll())
                .containsExactlyInAnyOrder(contracts);
    }
}
