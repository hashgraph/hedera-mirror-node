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
class TokenAccountHistoryRepositoryTest extends ImporterIntegrationTest {

    private final TokenAccountHistoryRepository tokenAccountHistoryRepository;

    @Test
    void prune() {
        domainBuilder.tokenAccountHistory().persist();
        var tokenAccountHistory2 = domainBuilder.tokenAccountHistory().persist();
        var tokenAccountHistory3 = domainBuilder.tokenAccountHistory().persist();

        tokenAccountHistoryRepository.prune(tokenAccountHistory2.getTimestampUpper());

        assertThat(tokenAccountHistoryRepository.findAll()).containsExactly(tokenAccountHistory3);
    }

    @Test
    void save() {
        var tokenAccountHistory = domainBuilder.tokenAccountHistory().get();
        tokenAccountHistoryRepository.save(tokenAccountHistory);
        assertThat(tokenAccountHistoryRepository.findById(tokenAccountHistory.getId()))
                .get()
                .isEqualTo(tokenAccountHistory);
    }
}
