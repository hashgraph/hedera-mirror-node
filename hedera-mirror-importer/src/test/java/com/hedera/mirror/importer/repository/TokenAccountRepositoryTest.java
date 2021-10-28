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

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityType;
import com.hedera.mirror.importer.domain.TokenAccount;
import com.hedera.mirror.importer.domain.TokenFreezeStatusEnum;
import com.hedera.mirror.importer.domain.TokenKycStatusEnum;

class TokenAccountRepositoryTest extends AbstractRepositoryTest {

    @Resource
    protected TokenAccountRepository tokenAccountRepository;

    private final EntityId tokenId = EntityId.of("0.0.101", EntityType.TOKEN);
    private final EntityId accountId = EntityId.of("0.0.102", EntityType.ACCOUNT);

    @Test
    void save() {
        TokenAccount token = tokenAccountRepository.save(tokenAccount(tokenId, accountId, 1, 1));
        assertThat(tokenAccountRepository.findById(token.getId()))
                .get()
                .isNotNull()
                .isEqualTo(token);
    }

    private TokenAccount tokenAccount(EntityId tokenId, EntityId accountId, long createdTimestamp,
                                      long modifiedTimestamp) {
        TokenAccount tokenAccount = new TokenAccount(tokenId, accountId, modifiedTimestamp);
        tokenAccount.setAssociated(true);
        tokenAccount.setAutomaticAssociation(false);
        tokenAccount.setKycStatus(TokenKycStatusEnum.NOT_APPLICABLE);
        tokenAccount.setFreezeStatus(TokenFreezeStatusEnum.NOT_APPLICABLE);
        tokenAccount.setCreatedTimestamp(createdTimestamp);
        return tokenAccount;
    }
}
