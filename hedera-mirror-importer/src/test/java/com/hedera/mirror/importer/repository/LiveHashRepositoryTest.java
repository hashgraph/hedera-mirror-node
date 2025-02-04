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
class LiveHashRepositoryTest extends ImporterIntegrationTest {

    private final LiveHashRepository liveHashRepository;

    @Test
    void prune() {
        domainBuilder.liveHash().persist();
        var liveHash2 = domainBuilder.liveHash().persist();
        var liveHash3 = domainBuilder.liveHash().persist();

        liveHashRepository.prune(liveHash2.getConsensusTimestamp());

        assertThat(liveHashRepository.findAll()).containsExactly(liveHash3);
    }

    @Test
    void save() {
        var liveHash = domainBuilder.liveHash().get();
        liveHash = liveHashRepository.save(liveHash);

        assertThat(liveHashRepository.findById(liveHash.getId())).get().isEqualTo(liveHash);
    }
}
