package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.common.domain.entity.TokenAllowance;

class TokenAllowanceRepositoryTest extends AbstractRepositoryTest {

    private static final RowMapper<TokenAllowance> ROW_MAPPER = rowMapper(TokenAllowance.class);

    @Resource
    private TokenAllowanceRepository tokenAllowanceRepository;

    @Test
    void save() {
        TokenAllowance tokenAllowance = domainBuilder.tokenAllowance().persist();
        assertThat(tokenAllowanceRepository.findById(tokenAllowance.getId())).get().isEqualTo(tokenAllowance);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        TokenAllowance tokenAllowance = domainBuilder.tokenAllowance().persist();

        jdbcOperations.update("insert into token_allowance_history select * from token_allowance");
        List<TokenAllowance> tokenAllowanceHistory = jdbcOperations.query("select * from token_allowance_history",
                ROW_MAPPER);

        assertThat(tokenAllowanceRepository.findAll()).containsExactly(tokenAllowance);
        assertThat(tokenAllowanceHistory).containsExactly(tokenAllowance);
    }
}
