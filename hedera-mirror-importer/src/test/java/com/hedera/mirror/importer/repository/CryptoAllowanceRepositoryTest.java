/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

class CryptoAllowanceRepositoryTest extends AbstractRepositoryTest {

    private static final RowMapper<CryptoAllowance> ROW_MAPPER = rowMapper(CryptoAllowance.class);

    @Resource
    private CryptoAllowanceRepository cryptoAllowanceRepository;

    @Test
    void save() {
        CryptoAllowance cryptoAllowance = domainBuilder.cryptoAllowance().persist();
        assertThat(cryptoAllowanceRepository.findById(cryptoAllowance.getId()))
                .get()
                .isEqualTo(cryptoAllowance);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        CryptoAllowance cryptoAllowance = domainBuilder.cryptoAllowance().persist();

        jdbcOperations.update("insert into crypto_allowance_history select * from crypto_allowance");
        List<CryptoAllowance> cryptoAllowanceHistory =
                jdbcOperations.query("select * from crypto_allowance_history", ROW_MAPPER);

        assertThat(cryptoAllowanceRepository.findAll()).containsExactly(cryptoAllowance);
        assertThat(cryptoAllowanceHistory).containsExactly(cryptoAllowance);
    }
}
