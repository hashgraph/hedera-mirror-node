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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.AccountBalance;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenBalance;

class AccountBalanceRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private AccountBalanceRepository accountBalanceRepository;

    @Test
    void findByConsensusTimestamp() {
        AccountBalance accountBalance1 = create(1L, 1, 100, 0);
        AccountBalance accountBalance2 = create(1L, 2, 200, 3);
        create(2L, 1, 50, 1);

        List<AccountBalance> result = accountBalanceRepository.findByIdConsensusTimestamp(1);
        result.forEach(ab -> ab.getTokenBalances().sort(Comparator.comparing(o -> o.getId().getTokenId())));
        assertThat(result)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrder(accountBalance1, accountBalance2);
    }

    private AccountBalance create(long consensusTimestamp, int accountNum, long balance, int numberOfTokenBalances) {
        AccountBalance.Id id = new AccountBalance.Id();
        id.setConsensusTimestamp(consensusTimestamp);
        id.setAccountId(EntityId.of(0, 0, accountNum, EntityTypeEnum.ACCOUNT));

        AccountBalance accountBalance = new AccountBalance();
        accountBalance.setBalance(balance);
        accountBalance.setId(id);
        accountBalance
                .setTokenBalances(createTokenBalances(consensusTimestamp, accountNum, balance, numberOfTokenBalances));
        return accountBalanceRepository.save(accountBalance);
    }

    private List<TokenBalance> createTokenBalances(long consensusTimestamp, int accountNum, long balance,
                                                   int numberOfBalances) {
        List<TokenBalance> tokenBalanceList = new ArrayList<>();
        for (int i = 1; i <= numberOfBalances; i++) {
            TokenBalance tokenBalance = new TokenBalance();
            TokenBalance.Id id = new TokenBalance.Id();
            id.setAccountId(EntityId.of(0, 0, accountNum, EntityTypeEnum.ACCOUNT));
            id.setConsensusTimestamp(consensusTimestamp);
            id.setTokenId(EntityId.of(0, 1, i, EntityTypeEnum.TOKEN));
            tokenBalance.setBalance(balance);
            tokenBalance.setId(id);
            tokenBalanceList.add(tokenBalance);
        }
        return tokenBalanceList;
    }
}
