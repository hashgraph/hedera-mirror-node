package com.hedera.mirror.importer.parser.batch;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;

import com.hedera.mirror.importer.domain.ContractResult;

import com.hedera.mirror.importer.repository.ContractResultRepository;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.domain.Contract;
import com.hedera.mirror.importer.domain.DomainBuilder;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.repository.ContractRepository;
import com.hedera.mirror.importer.repository.EntityRepository;

class CompositeBatchPersisterTest extends IntegrationTest {

    @Resource
    private CompositeBatchPersister compositeBatchInserter;

    @Resource
    private DomainBuilder domainBuilder;

    @Resource
    private ContractRepository contractRepository;

    @Resource
    private ContractResultRepository contractResultRepository;

    @Test
    @Transactional
    void persist() {
        Contract contract = domainBuilder.contract().get();
        ContractResult contractResult = domainBuilder.contractResult().get();

        compositeBatchInserter.persist(List.of(contract));
        compositeBatchInserter.persist(List.of(contractResult));

        assertThat(contractRepository.findAll()).containsExactly(contract);
        assertThat(contractResultRepository.findAll()).containsExactly(contractResult);
    }

    @Test
    void persistEmpty() {
        compositeBatchInserter.persist(null);
        compositeBatchInserter.persist(List.of());
    }

    @Test
    void persistNullItem() {
        List<Object> items = new ArrayList<>();
        items.add(null);
        assertThatThrownBy(() -> compositeBatchInserter.persist(items))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void persistUnknown() {
        assertThatThrownBy(() -> compositeBatchInserter.persist(List.of(new Object())))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
