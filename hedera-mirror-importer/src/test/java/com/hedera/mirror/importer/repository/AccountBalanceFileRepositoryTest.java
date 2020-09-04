package com.hedera.mirror.importer.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.mirror.importer.domain.AccountBalance;

import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.AccountBalanceFile;
import com.hedera.mirror.importer.domain.EntityId;

class AccountBalanceFileRepositoryTest extends AbstractRepositoryTest {

    @Test
    void insert() {
        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        AccountBalanceFile accountBalanceFile = AccountBalanceFile.builder()
                .consensusTimestamp(1598810507023456789L)
                .count(0L)
                .fileHash("fileHash")
                .loadEnd(0L)
                .loadStart(0L)
                .name("2019-08-30T18_15_00.016002001Z_Balances.csv")
                .nodeAccountId(nodeAccountId)
                .build();
        accountBalanceFile = accountBalanceFileRepository.save(accountBalanceFile);
        assertThat(accountBalanceFileRepository.findById(accountBalanceFile.getConsensusTimestamp()).get())
                .isNotNull()
                .isEqualTo(accountBalanceFile);
    }
}
