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

import com.hedera.mirror.common.domain.entity.NftAllowance;
import jakarta.annotation.Resource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;

class NftAllowanceRepositoryTest extends AbstractRepositoryTest {

    private static final RowMapper<NftAllowance> ROW_MAPPER = rowMapper(NftAllowance.class);

    @Resource
    private NftAllowanceRepository nftAllowanceRepository;

    @Test
    void save() {
        NftAllowance nftAllowance = domainBuilder.nftAllowance().persist();
        assertThat(nftAllowanceRepository.findById(nftAllowance.getId())).get().isEqualTo(nftAllowance);
    }

    /**
     * This test verifies that the domain object and table definition are in sync with the history table.
     */
    @Test
    void history() {
        NftAllowance nftAllowance = domainBuilder.nftAllowance().persist();

        jdbcOperations.update("insert into nft_allowance_history select * from nft_allowance");
        List<NftAllowance> nftAllowanceHistory =
                jdbcOperations.query("select * from nft_allowance_history", ROW_MAPPER);

        assertThat(nftAllowanceRepository.findAll()).containsExactly(nftAllowance);
        assertThat(nftAllowanceHistory).containsExactly(nftAllowance);
    }
}
