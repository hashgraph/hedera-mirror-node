/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.balance;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.entity.EntityId;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.Assert;

@Named
@RequiredArgsConstructor
public class AccountBalanceBuilder {

    private final DomainBuilder domainBuilder;

    public Builder accountBalance() {
        return accountBalance(domainBuilder.timestamp());
    }

    public Builder accountBalance(long consensusTimestamp) {
        return new Builder(consensusTimestamp);
    }

    public static class Builder {

        private final long consensusTimestamp;
        private final List<TokenBalance> tokenBalances = new ArrayList<>();

        private EntityId accountId;
        private long balance;

        private Builder(long consensusTimestamp) {
            this.consensusTimestamp = consensusTimestamp;
        }

        public Builder accountId(long accountId) {
            return accountId(EntityId.of(accountId, ACCOUNT));
        }

        public Builder accountId(EntityId accountId) {
            Assert.isNull(this.accountId, "AccountId is already set");
            this.accountId = accountId;
            return this;
        }

        public Builder balance(long balance) {
            this.balance = balance;
            return this;
        }

        public Builder tokenBalance(long balance, long tokenId) {
            return tokenBalance(balance, EntityId.of(tokenId, TOKEN));
        }

        public Builder tokenBalance(long balance, EntityId tokenId) {
            Assert.notNull(this.accountId, "Must set accountId");
            var tokenBalance = TokenBalance.builder()
                    .balance(balance)
                    .id(new TokenBalance.Id(consensusTimestamp, accountId, tokenId))
                    .build();
            tokenBalances.add(tokenBalance);
            return this;
        }

        public AccountBalance build() {
            return AccountBalance.builder()
                    .balance(balance)
                    .id(new AccountBalance.Id(consensusTimestamp, accountId))
                    .tokenBalances(tokenBalances)
                    .build();
        }
    }
}
