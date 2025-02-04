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
class TokenAllowanceHistoryRepositoryTest extends ImporterIntegrationTest {

    private final TokenAllowanceHistoryRepository tokenAllowanceHistoryRepository;

    @Test
    void prune() {
        domainBuilder.tokenAllowanceHistory().persist();
        var tokenAllowanceHistory2 = domainBuilder.tokenAllowanceHistory().persist();
        var tokenAllowanceHistory3 = domainBuilder.tokenAllowanceHistory().persist();

        tokenAllowanceHistoryRepository.prune(tokenAllowanceHistory2.getTimestampUpper());

        assertThat(tokenAllowanceHistoryRepository.findAll()).containsExactly(tokenAllowanceHistory3);
    }

    @Test
    void save() {
        var tokenAllowanceHistory = domainBuilder.tokenAllowanceHistory().get();
        tokenAllowanceHistoryRepository.save(tokenAllowanceHistory);
        assertThat(tokenAllowanceHistoryRepository.findById(tokenAllowanceHistory.getId()))
                .get()
                .isEqualTo(tokenAllowanceHistory);
    }
}
