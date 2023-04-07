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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.web3.Web3IntegrationTest;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class CustomFeeRepositoryTest extends Web3IntegrationTest {
    private final CustomFeeRepository customFeeRepository;
    private final CustomFeeRepositoryImpl customFeeRepositoryImpl;

    @Test
    void findByTokenId() {
        final var customFee = persistDifferentFeesWithSameId();
        final var tokenId = customFee.get(0).getId().getTokenId().getId();

        assertThat(customFeeRepositoryImpl.findByTokenId(tokenId).get(0).getAmount()).isEqualTo(customFee.get(0).getAmount());
        assertThat(customFeeRepositoryImpl.findByTokenId(tokenId).get(1).getAmountDenominator()).isEqualTo(customFee.get(1).getAmountDenominator());
    }

    private List<CustomFee> persistDifferentFeesWithSameId() {
        var rightEntityId = EntityId.of(0, 0, 12, EntityType.TOKEN);
        var timestamp = System.currentTimeMillis();

        var fee1 = domainBuilder.customFee().customize(fee -> fee
                        .id(new CustomFee.Id(timestamp, rightEntityId))
                        .amount((long) 1).amountDenominator(100L))
                .persist();
        var fee2 = domainBuilder.customFee().customize(fee -> fee
                        .id(new CustomFee.Id(timestamp, rightEntityId))
                        .amount((long) 2).amountDenominator(200L))
                .persist();

        return List.of(fee1, fee2);
    }
}
