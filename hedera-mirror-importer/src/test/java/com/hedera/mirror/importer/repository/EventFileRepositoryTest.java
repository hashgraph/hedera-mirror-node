/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EventFileRepositoryTest extends AbstractRepositoryTest {

    private final EventFileRepository eventFileRepository;

    @Test
    void findLatest() {
        var eventFile1 = domainBuilder.eventFile().get();
        var eventFile2 = domainBuilder.eventFile().get();
        var eventFile3 = domainBuilder.eventFile().get();
        eventFileRepository.saveAll(List.of(eventFile1, eventFile2, eventFile3));
        assertThat(eventFileRepository.findLatest()).get().isEqualTo(eventFile3);
    }

    @Test
    void prune() {
        domainBuilder.eventFile().persist();
        var eventFile2 = domainBuilder.eventFile().persist();
        var eventFile3 = domainBuilder.eventFile().persist();

        eventFileRepository.prune(eventFile2.getConsensusEnd());

        assertThat(eventFileRepository.findAll()).containsExactly(eventFile3);
    }
}
