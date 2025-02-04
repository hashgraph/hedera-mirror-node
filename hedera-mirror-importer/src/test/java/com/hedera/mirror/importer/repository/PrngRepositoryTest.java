/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class PrngRepositoryTest extends ImporterIntegrationTest {

    private final PrngRepository prngRepository;

    @Test
    void prune() {
        domainBuilder.prng().persist();
        var prng2 = domainBuilder.prng().persist();
        var prng3 = domainBuilder.prng().persist();

        prngRepository.prune(prng2.getId());

        assertThat(prngRepository.findAll()).containsExactly(prng3);
    }

    @Test
    void save() {
        var prng = domainBuilder.prng().get();

        prngRepository.save(prng);
        assertThat(prngRepository.findAll()).contains(prng);
    }
}
