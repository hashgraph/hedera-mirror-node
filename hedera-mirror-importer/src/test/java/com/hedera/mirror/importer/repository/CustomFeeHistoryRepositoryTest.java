/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class CustomFeeHistoryRepositoryTest extends ImporterIntegrationTest {

    private final CustomFeeHistoryRepository repository;

    @Test
    void prune() {
        // given
        var history1 = domainBuilder.customFeeHistory().persist();
        var history2 = domainBuilder
                .customFeeHistory()
                .customize(n -> n.timestampRange(
                        Range.closedOpen(history1.getTimestampUpper(), history1.getTimestampUpper() + 5)))
                .persist();
        var history3 = domainBuilder
                .customFeeHistory()
                .customize(n -> n.timestampRange(
                        Range.closedOpen(history2.getTimestampUpper(), history2.getTimestampUpper() + 5)))
                .persist();

        // when
        repository.prune(history2.getTimestampLower());

        // then
        assertThat(repository.findAll()).containsExactlyInAnyOrder(history2, history3);

        // when
        repository.prune(history3.getTimestampLower() + 1);

        // then
        assertThat(repository.findAll()).containsExactly(history3);
    }

    @Test
    void save() {
        var customFeeHistory = domainBuilder.customFeeHistory().get();
        repository.save(customFeeHistory);
        assertThat(repository.findAll()).containsExactly(customFeeHistory);
        assertThat(repository.findById(customFeeHistory.getEntityId())).get().isEqualTo(customFeeHistory);
    }
}
