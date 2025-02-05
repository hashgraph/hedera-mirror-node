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

import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

@RequiredArgsConstructor
class CustomFeeRepositoryTest extends ImporterIntegrationTest {

    private final CustomFeeRepository customFeeRepository;
    private static final RowMapper<CustomFee> ROW_MAPPER = rowMapper(CustomFee.class);

    @Test
    void save() {
        var customFee = domainBuilder.customFee().get();
        customFeeRepository.save(customFee);
        assertThat(customFeeRepository.findById(customFee.getEntityId())).get().isEqualTo(customFee);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        var customFee = domainBuilder.customFee().persist();

        jdbcOperations.update("insert into custom_fee_history select * from custom_fee");
        List<CustomFee> customFeeHistory = jdbcOperations.query("select * from custom_fee_history", ROW_MAPPER);

        assertThat(customFeeRepository.findAll()).containsExactly(customFee);
        assertThat(customFeeHistory).containsExactly(customFee);
    }
}
