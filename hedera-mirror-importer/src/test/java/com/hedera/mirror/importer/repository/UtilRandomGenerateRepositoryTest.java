package com.hedera.mirror.importer.repository;

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

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class UtilRandomGenerateRepositoryTest extends AbstractRepositoryTest {

    private final RandomGenerateRepository utilRandomGenerateRepository;

    @Test
    void prune() {
        domainBuilder.utilRandomGenerate().persist();
        var randomGenerate2 = domainBuilder.utilRandomGenerate().persist();
        var randomGenerate3 = domainBuilder.utilRandomGenerate().persist();

        utilRandomGenerateRepository.prune(randomGenerate2.getId());

        assertThat(utilRandomGenerateRepository.findAll()).containsExactly(randomGenerate3);
    }

    @Test
    void save() {
        var utilRandomGenerate = domainBuilder.utilRandomGenerate().get();

        utilRandomGenerateRepository.save(utilRandomGenerate);
        assertThat(utilRandomGenerateRepository.findAll()).contains(utilRandomGenerate);
    }
}
