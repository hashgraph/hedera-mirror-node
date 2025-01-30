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
class TopicMessageRepositoryTest extends ImporterIntegrationTest {

    private final TopicMessageRepository topicMessageRepository;

    @Test
    void prune() {
        domainBuilder.topicMessage().persist();
        var topicMessage2 = domainBuilder.topicMessage().persist();
        var topicMessage3 = domainBuilder.topicMessage().persist();

        topicMessageRepository.prune(topicMessage2.getConsensusTimestamp());

        assertThat(topicMessageRepository.findAll()).containsExactly(topicMessage3);
    }

    @Test
    void save() {
        var topicMessage = domainBuilder.topicMessage().get();
        topicMessageRepository.save(topicMessage);
        assertThat(topicMessageRepository.findById(topicMessage.getId())).get().isEqualTo(topicMessage);
    }
}
