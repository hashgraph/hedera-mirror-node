package com.hedera.mirror.web3.repository;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftRepositoryTest extends Web3IntegrationTest {
    private final NftRepository nftRepository;

    @Test
    void findById() {
        final var spender = new EntityId(0L, 0L, 56L, EntityType.ACCOUNT);
        final var nft = domainBuilder.nft().customize(n -> n.spender(spender)).persist();

        assertThat(nftRepository.findById(nft.getId()).get())
                .returns(nft.getSpender(), Nft::getSpender)
                .returns(nft.getAccountId(), Nft::getAccountId)
                .returns(nft.getMetadata(), Nft::getMetadata);
    }
}
