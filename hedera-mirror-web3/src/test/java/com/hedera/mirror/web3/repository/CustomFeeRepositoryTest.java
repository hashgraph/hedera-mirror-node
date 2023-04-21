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

package com.hedera.mirror.web3.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class CustomFeeRepositoryTest extends Web3IntegrationTest {
    private final CustomFeeRepository customFeeRepository;

    @Test
    void findByTokenId() {
        var customFee1 = domainBuilder.customFee().persist();
        var customFee2 = domainBuilder
                .customFee()
                .customize(c -> c.id(customFee1.getId()).amount(12L))
                .persist();
        final var tokenId = customFee1.getId().getTokenId().getId();

        assertThat(customFeeRepository.findByTokenId(tokenId))
                .hasSize(2)
                .extracting(CustomFee::getAmount)
                .containsExactlyInAnyOrder(customFee1.getAmount(), customFee2.getAmount());
    }
}
