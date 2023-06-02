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

import com.hedera.mirror.common.domain.token.TokenAccount;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

class TokenAccountRepositoryTest extends AbstractRepositoryTest {

    private static final RowMapper<TokenAccount> ROW_MAPPER = rowMapper(TokenAccount.class);

    @Resource
    protected TokenAccountRepository tokenAccountRepository;

    @Test
    void save() {
        var tokenAccount = domainBuilder.tokenAccount().get();
        tokenAccountRepository.save(tokenAccount);
        assertThat(tokenAccountRepository.findById(tokenAccount.getId())).get().isEqualTo(tokenAccount);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        var tokenAccount = domainBuilder.tokenAccount().persist();

        jdbcOperations.update("insert into token_account_history select * from token_account");
        List<TokenAccount> tokenAccountHistory =
                jdbcOperations.query("select * from token_account_history", ROW_MAPPER);

        assertThat(tokenAccountRepository.findAll()).containsExactly(tokenAccount);
        assertThat(tokenAccountHistory).containsExactly(tokenAccount);
    }
}
