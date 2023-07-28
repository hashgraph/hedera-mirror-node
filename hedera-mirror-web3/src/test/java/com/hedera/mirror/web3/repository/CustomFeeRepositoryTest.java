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

import com.hedera.mirror.common.domain.transaction.FixedFee;
import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class CustomFeeRepositoryTest extends Web3IntegrationTest {
    private final CustomFeeRepository customFeeRepository;

    @Test
    void findByTokenId() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getTokenId();

        var result = customFeeRepository.findById(tokenId);
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        var listAssert = assertThat(fixedFeesResult).hasSize(1);
        listAssert.allSatisfy(fee -> fee.getAmount().equals(amount));
    }
}
