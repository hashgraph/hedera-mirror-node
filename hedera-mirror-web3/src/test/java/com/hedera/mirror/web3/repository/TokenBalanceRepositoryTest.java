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

import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TokenBalanceRepositoryTest extends Web3IntegrationTest {

    private final TokenBalanceRepository tokenBalanceRepository;

    @Test
    void findById() {
        var tokenBalance1 = domainBuilder.tokenBalance().get();
        var tokenBalance2 = domainBuilder.tokenBalance().get();
        var tokenBalance3 = domainBuilder.tokenBalance().get();

        tokenBalanceRepository.saveAll(List.of(tokenBalance1, tokenBalance2, tokenBalance3));
        Assertions.assertThat(tokenBalanceRepository.findById(tokenBalance1.getId()))
                .get()
                .isEqualTo(tokenBalance1);
        Assertions.assertThat(tokenBalanceRepository.findAll())
                .containsExactlyInAnyOrder(tokenBalance1, tokenBalance2, tokenBalance3);
    }
}
