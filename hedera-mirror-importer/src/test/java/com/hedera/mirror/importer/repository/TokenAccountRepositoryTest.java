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

import java.util.Optional;
import javax.annotation.Resource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;

public class TokenAccountRepositoryTest extends AbstractRepositoryTest {
    @Resource
    protected TokenAccountRepository tokenAccountRepository;

    private final String tokenId = "0.0.101";
    private final String accountId = "0.0.102";

    @Test
    void save() {
        TokenAccount token = tokenAccountRepository.save(tokenAccount(tokenId, accountId, 1));
        Assertions.assertThat(tokenAccountRepository.findById(token.getId()).get())
                .isNotNull()
                .isEqualTo(token);
    }

    @Test
    void findByTokenIdAndAccountId() {
        tokenAccountRepository.save(tokenAccount(tokenId, accountId, 1));
        String tokenId2 = "0.2.22";
        String accountId2 = "0.2.44";
        TokenAccount token2 = tokenAccountRepository.save(tokenAccount(tokenId2, accountId2, 2));
        tokenAccountRepository.save(tokenAccount("1.0.7", "1.0.34", 3));
        Assertions.assertThat(tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenId2, EntityTypeEnum.TOKEN).getId(), EntityId
                        .of(accountId2, EntityTypeEnum.ACCOUNT).getId()).get())
                .isNotNull()
                .isEqualTo(token2);

        Assertions.assertThat(tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of("1.2.3", EntityTypeEnum.TOKEN).getId(), EntityId
                        .of("0.2.44", EntityTypeEnum.ACCOUNT).getId())).isNotPresent();
    }

    @Test
    void findByTokenIdAndAccountIdMultipleTokensSameAccount() {
        String tokenId2 = "0.2.22";
        String accountId2 = "0.0.44";
        long createTimestamp1 = 55;
        long createTimestamp2 = 66;
        tokenAccountRepository.save(tokenAccount(tokenId, accountId, createTimestamp1));
        tokenAccountRepository
                .save(tokenAccount(tokenId, accountId, createTimestamp2));
        TokenAccount tokenAccount_1_2 = tokenAccountRepository
                .save(tokenAccount(tokenId2, accountId, createTimestamp2));
        Assertions.assertThat(tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenId2, EntityTypeEnum.TOKEN).getId(), EntityId
                        .of(accountId, EntityTypeEnum.ACCOUNT).getId()).get())
                .isNotNull()
                .isEqualTo(tokenAccount_1_2);

        Assertions.assertThat(tokenAccountRepository
                .findByTokenIdAndAccountId(EntityId.of(tokenId2, EntityTypeEnum.TOKEN).getId(), EntityId
                        .of(accountId2, EntityTypeEnum.ACCOUNT).getId())).isNotPresent();
    }

    private TokenAccount tokenAccount(String tokenId, String accountId, long createdTimestamp) {
        TokenAccount tokenAccount = new TokenAccount(EntityId
                .of(tokenId, EntityTypeEnum.TOKEN), EntityId.of(accountId, EntityTypeEnum.ACCOUNT));
        tokenAccount.setAssociated(true);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        tokenAccount.setModifiedTimestamp(createdTimestamp);
        return tokenAccount;
    }
}
