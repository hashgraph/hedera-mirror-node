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

import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
class TokenAirdropRepositoryTest extends ImporterIntegrationTest {

    private final TokenAirdropRepository repository;

    @Test
    void saveFungible() {
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON).get();
        repository.save(tokenAirdrop);
        assertThat(repository.findAll()).containsOnly(tokenAirdrop);
    }

    @Test
    void saveNft() {
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.NON_FUNGIBLE_UNIQUE).get();
        repository.save(tokenAirdrop);
        assertThat(repository.findAll()).containsOnly(tokenAirdrop);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        var tokenAirdrop =
                domainBuilder.tokenAirdrop(TokenTypeEnum.FUNGIBLE_COMMON).persist();

        jdbcOperations.update("insert into token_airdrop_history select * from token_airdrop");
        var tokenAirdropHistory = findHistory(TokenAirdrop.class);

        assertThat(repository.findAll()).containsExactly(tokenAirdrop);
        assertThat(tokenAirdropHistory).containsExactly(tokenAirdrop);
    }
}
