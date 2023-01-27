package com.hedera.mirror.importer.repository;

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

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ReconciliationJobRepositoryTest extends AbstractRepositoryTest {

    private final ReconciliationJobRepository reconciliationJobRepository;

    @Test
    void findLatest() {
        assertThat(reconciliationJobRepository.findLatest()).isEmpty();

        domainBuilder.reconciliationJob().persist();
        domainBuilder.reconciliationJob().persist();
        var reconciliationJob3 = domainBuilder.reconciliationJob().persist();

        assertThat(reconciliationJobRepository.findLatest()).get().isEqualTo(reconciliationJob3);
    }

    @Test
    void prune() {
        domainBuilder.reconciliationJob().persist();
        var reconciliationJob2 = domainBuilder.reconciliationJob().persist();
        var reconciliationJob3 = domainBuilder.reconciliationJob().persist();

        reconciliationJobRepository.prune(reconciliationJob2.getConsensusTimestamp());

        assertThat(reconciliationJobRepository.findAll()).containsExactly(reconciliationJob3);
    }
}
