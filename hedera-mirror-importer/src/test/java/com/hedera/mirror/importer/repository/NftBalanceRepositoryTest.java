package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.importer.domain.EntityTypeEnum.ACCOUNT;
import static com.hedera.mirror.importer.domain.EntityTypeEnum.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.NftBalance;

class NftBalanceRepositoryTest extends AbstractRepositoryTest {

    private static final EntityId ACCOUNT_ID = EntityId.of("0.0.10", ACCOUNT);
    private static final EntityId TOKEN_ID = EntityId.of("0.0.200", TOKEN);

    @Resource
    private NftBalanceRepository nftBalanceRepository;

    @Test
    void getLastTimestamp() {
        nftBalanceRepository.saveAll(List.of(
                new NftBalance(10L, ACCOUNT_ID, 1L, TOKEN_ID),
                new NftBalance(20L, ACCOUNT_ID, 1L, TOKEN_ID)
        ));
        assertThat(nftBalanceRepository.getLastTimestamp()).get().isEqualTo(20L);
    }

    @Test
    void getLastTimestampNotPresent() {
        assertThat(nftBalanceRepository.getLastTimestamp()).isNotPresent();
    }

    @Test
    void save() {
        NftBalance nftBalance = new NftBalance(10L, ACCOUNT_ID, 1L, TOKEN_ID);
        nftBalanceRepository.save(nftBalance);
        assertThat(nftBalanceRepository.findAll()).containsOnly(nftBalance);
    }
}
