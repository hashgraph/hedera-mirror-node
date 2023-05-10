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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.AbstractNftAllowance;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.web3.Web3IntegrationTest;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class NftAllowanceRepositoryTest extends Web3IntegrationTest {
    private final NftAllowanceRepository allowanceRepository;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void spenderHasApproveForAll(boolean isApproveForAll) {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(isApproveForAll))
                .persist();

        assertThat(allowanceRepository
                        .findById(allowance.getId())
                        .map(NftAllowance::isApprovedForAll)
                        .orElse(false))
                .isEqualTo(isApproveForAll);
    }

    @Test
    void findBySpenderAndApprovedForAllIsTrue() {
        final var allowance = domainBuilder
                .nftAllowance()
                .customize(a -> a.approvedForAll(true))
                .persist();
        assertThat(allowanceRepository.findByOwnerAndApprovedForAllIsTrue(allowance.getOwner()))
                .hasSize(1)
                .contains(allowance);
    }

    @Test
    void noMatchingRecord() {
        assertThat(allowanceRepository
                        .findById(new AbstractNftAllowance.Id())
                        .map(NftAllowance::isApprovedForAll)
                        .orElse(false))
                .isFalse();
    }
}
