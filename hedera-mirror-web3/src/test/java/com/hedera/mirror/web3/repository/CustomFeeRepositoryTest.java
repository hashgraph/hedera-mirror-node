/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.web3.Web3IntegrationTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;

@RequiredArgsConstructor
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
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampLessThenBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getTokenId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower() + 1);
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampEqualToBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getTokenId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower());
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampGreaterThanBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFee()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getTokenId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower() - 1);
        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndTimestampHistoricalLessThenBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFeeHistory()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getTokenId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower() + 1);
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampHistoricalEqualToBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFeeHistory()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getTokenId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower());
        assertThat(result).isPresent();
        var fixedFeesResult = result.get().getFixedFees();
        assertThat(fixedFeesResult).hasSize(1);
        assertThat(fixedFeesResult.get(0).getAmount()).isEqualTo(amount);
    }

    @Test
    void findByIdAndTimestampHistoricalGreaterThanBlock() {
        long amount = 12L;
        var fixedFee = FixedFee.builder().amount(amount).build();
        var customFee1 = domainBuilder
                .customFeeHistory()
                .customize(c -> c.fixedFees(List.of(fixedFee)))
                .persist();
        final var tokenId = customFee1.getTokenId();

        var result = customFeeRepository.findByTokenIdAndTimestamp(tokenId, customFee1.getTimestampLower() - 1);
        assertThat(result).isEmpty();
    }
}
