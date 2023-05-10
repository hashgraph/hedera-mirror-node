/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.repository;

import static com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum.FROZEN;
import static com.hedera.mirror.common.domain.token.TokenKycStatusEnum.GRANTED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class TokenAccountRepositoryTest extends Web3IntegrationTest {
    private final TokenAccountRepository repository;

    @Test
    void findById() {
        final var tokenAccount = domainBuilder
                .tokenAccount()
                .customize(a -> a.freezeStatus(FROZEN).kycStatus(GRANTED))
                .persist();

        assertThat(repository.findById(tokenAccount.getId()).get())
                .returns(tokenAccount.getFreezeStatus(), TokenAccount::getFreezeStatus)
                .returns(tokenAccount.getKycStatus(), TokenAccount::getKycStatus)
                .returns(tokenAccount.getBalance(), TokenAccount::getBalance);
    }

    @Test
    void countByAccountIdAndAssociationIsTrue() {
        long accountId = 22L;
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(true).accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(true).accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.associated(false).accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndAssociatedIsTrue(accountId)).isEqualTo(2);
    }

    @Test
    void countByAccountIdAndPositiveBalance() {
        long accountId = 22L;
        domainBuilder
                .tokenAccount()
                .customize(a -> a.balance(33).accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.balance(44).accountId(accountId))
                .persist();
        domainBuilder
                .tokenAccount()
                .customize(a -> a.balance(0).accountId(accountId))
                .persist();

        assertThat(repository.countByAccountIdAndPositiveBalance(accountId)).isEqualTo(2);
    }
}
