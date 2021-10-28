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

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TokenTransfer;

class TokenTransferRepositoryTest extends AbstractRepositoryTest {

    @Resource
    private TokenTransferRepository tokenTransferRepository;

    @Test
    void findById() {
        EntityId tokenId = EntityId.of(0L, 1L, 20L, TOKEN);
        EntityId accountId = EntityId.of(0L, 1L, 7L, ACCOUNT);
        EntityId payerAccountId = EntityId.of(0L, 1L, 500L, ACCOUNT);
        long amount = 40L;
        TokenTransfer tokenTransfer = domainBuilder.tokenTransfer().customize(t -> t
                .amount(amount)
                .id(new TokenTransfer.Id(1L, tokenId, accountId))
                .payerAccountId(payerAccountId)
                .tokenDissociate(false)).get();

        tokenTransferRepository.save(tokenTransfer);

        assertThat(tokenTransferRepository.findById(tokenTransfer.getId()))
                .get()
                .isEqualTo(tokenTransfer);
    }
}
