/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAirdropHistoryRepositoryTest extends ImporterIntegrationTest {

    private final TokenAirdropHistoryRepository tokenAirdropHistoryRepository;

    @Test
    void prune() {
        domainBuilder.tokenAirdropHistory(TokenTypeEnum.NON_FUNGIBLE_UNIQUE).persist();
        var tokenAirdropHistory2 =
                domainBuilder.tokenAirdropHistory(TokenTypeEnum.FUNGIBLE_COMMON).persist();
        var tokenAirdropHistory3 = domainBuilder
                .tokenAirdropHistory(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .persist();

        tokenAirdropHistoryRepository.prune(tokenAirdropHistory2.getTimestampUpper());

        assertThat(tokenAirdropHistoryRepository.findAll()).containsExactly(tokenAirdropHistory3);
    }

    @Test
    void save() {
        var tokenAirdropHistory = domainBuilder
                .tokenAirdropHistory(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                .get();
        tokenAirdropHistoryRepository.save(tokenAirdropHistory);
        assertThat(tokenAirdropHistoryRepository.findAll()).containsOnly(tokenAirdropHistory);
    }
}
