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

import static org.assertj.core.api.Assertions.assertThat;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AccountBalanceSet;

public class AccountBalanceSetRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private AccountBalanceSetRepository accountBalanceSetRepository;

    @Test
    void save() {
        AccountBalanceSet accountBalanceSet1 = create(1L);
        AccountBalanceSet accountBalanceSet2 = create(2L);
        AccountBalanceSet accountBalanceSet3 = create(3L);

        assertThat(accountBalanceSetRepository.findById(accountBalanceSet1.getId())).get()
                .isEqualTo(accountBalanceSet1);
        assertThat(accountBalanceSetRepository.findAll())
                .containsExactlyInAnyOrder(accountBalanceSet1, accountBalanceSet2, accountBalanceSet3);
    }

    private AccountBalanceSet create(long consensusTimestamp) {
        AccountBalanceSet accountBalanceSet = new AccountBalanceSet();
        accountBalanceSet.setConsensusTimestamp(consensusTimestamp);
        return accountBalanceSetRepository.save(accountBalanceSet);
    }
}
