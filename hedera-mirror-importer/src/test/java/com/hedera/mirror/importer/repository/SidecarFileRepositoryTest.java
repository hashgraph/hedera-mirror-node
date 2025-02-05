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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class SidecarFileRepositoryTest extends ImporterIntegrationTest {

    private final SidecarFileRepository sidecarFileRepository;

    @Test
    void prune() {
        domainBuilder.sidecarFile().persist();
        var sidecarFile2 = domainBuilder.sidecarFile().persist();
        var sidecarFile3 = domainBuilder.sidecarFile().persist();

        sidecarFileRepository.prune(sidecarFile2.getConsensusEnd());

        assertThat(sidecarFileRepository.findAll()).containsOnly(sidecarFile3);
    }

    @Test
    void save() {
        var sidecarFile1 = domainBuilder.sidecarFile().get();
        var sidecarFile2 = domainBuilder.sidecarFile().get();
        sidecarFileRepository.saveAll(List.of(sidecarFile1, sidecarFile2));
        assertThat(sidecarFileRepository.findAll()).containsExactlyInAnyOrder(sidecarFile1, sidecarFile2);
    }
}
