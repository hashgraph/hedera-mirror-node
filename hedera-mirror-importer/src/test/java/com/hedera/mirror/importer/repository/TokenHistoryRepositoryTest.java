/*
 * Copyright (C) 2020-2025 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
class TokenHistoryRepositoryTest extends ImporterIntegrationTest {

    private static final RowMapper<Token> ROW_MAPPER = rowMapper(Token.class);
    private final TokenHistoryRepository tokenHistoryRepository;
    private final TokenRepository tokenRepository;

    @Test
    void save() {
        var tokenHistory = domainBuilder.tokenHistory().persist();
        assertThat(tokenHistoryRepository.findById(tokenHistory.getTokenId()))
                .get()
                .isEqualTo(tokenHistory);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        var token = domainBuilder.token().persist();

        jdbcOperations.update("insert into token_history select * from token");
        var tokenHistory = jdbcOperations.query("select * from token_history", ROW_MAPPER);

        assertThat(tokenRepository.findAll()).containsExactly(token);
        assertThat(tokenHistory).containsExactly(token);
    }
}
