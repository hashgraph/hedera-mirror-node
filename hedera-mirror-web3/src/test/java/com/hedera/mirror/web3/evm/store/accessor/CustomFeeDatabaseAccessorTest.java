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

package com.hedera.mirror.web3.evm.store.accessor;

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.FixedFee;
import com.hedera.mirror.common.domain.transaction.FractionalFee;
import com.hedera.mirror.common.domain.transaction.RoyaltyFee;
import com.hedera.mirror.web3.repository.CustomFeeRepository;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFeeDatabaseAccessorTest {
    @InjectMocks
    private CustomFeeDatabaseAccessor customFeeDatabaseAccessor;

    @Mock
    private CustomFeeRepository customFeeRepository;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    private final long tokenId = 123L;

    private final EntityId collectorId = new EntityId(1L, 2L, 3L, EntityType.ACCOUNT);

    private final Address collectorAddress = toAddress(collectorId);

    private final EntityId denominatingTokenId = new EntityId(11L, 12L, 13L, EntityType.TOKEN);

    private com.hedera.mirror.common.domain.transaction.CustomFee customFee;

    @BeforeEach
    void setup() {
        customFee = new com.hedera.mirror.common.domain.transaction.CustomFee();
    }

    @Test
    void royaltyFee() {
        var fixedFeeBuilderBuilder =
                FixedFee.builder().collectorAccountId(collectorId).denominatingTokenId(denominatingTokenId);
        var fallbackFee = fixedFeeBuilderBuilder.amount(20L).build();
        var fallbackFee2 = fixedFeeBuilderBuilder.amount(21L).build();

        var royaltyFeeBuilder = RoyaltyFee.builder();
        var royaltyFee = royaltyFeeBuilder
                .royaltyDenominator(10L)
                .royaltyNumerator(15L)
                .fallbackFee(fallbackFee)
                .build();
        var royaltyFee2 = royaltyFeeBuilder
                .royaltyDenominator(11L)
                .royaltyNumerator(16L)
                .fallbackFee(fallbackFee2)
                .build();
        var royaltyFees = List.of(royaltyFee, royaltyFee2);
        customFee.setRoyaltyFees(royaltyFees);

        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId)).thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : royaltyFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFixedFee()).isNull();
                assertThat(fee.getFractionalFee()).isNull();
                var resultRoyaltyFee = fee.getRoyaltyFee();
                assertThat(resultRoyaltyFee.getDenominator()).isEqualTo(domainFee.getRoyaltyDenominator());
                assertThat(resultRoyaltyFee.getNumerator()).isEqualTo(domainFee.getRoyaltyNumerator());
                assertThat(resultRoyaltyFee.getAmount())
                        .isEqualTo(domainFee.getFallbackFee().getAmount());
                assertThat(resultRoyaltyFee.getDenominatingTokenId()).isEqualTo(toAddress(denominatingTokenId));
                assertThat(resultRoyaltyFee.isUseHbarsForPayment()).isFalse();
                assertThat(resultRoyaltyFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void fractionFee() {
        var fractionalFeeBuilder = FractionalFee.builder().collectorAccountId(collectorId);
        var fractionalFee = fractionalFeeBuilder
                .amount(20L)
                .amountDenominator(2L)
                .maximumAmount(100L)
                .minimumAmount(5L)
                .netOfTransfers(true)
                .build();
        var fractionalFee2 = fractionalFeeBuilder
                .amount(21L)
                .amountDenominator(3L)
                .maximumAmount(101L)
                .minimumAmount(6L)
                .netOfTransfers(false)
                .build();
        var fractionalFees = List.of(fractionalFee, fractionalFee2);
        customFee.setFractionalFees(fractionalFees);

        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId)).thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : fractionalFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFixedFee()).isNull();
                assertThat(fee.getRoyaltyFee()).isNull();
                var resultFractionalFee = fee.getFractionalFee();
                assertThat(resultFractionalFee.getNumerator()).isEqualTo(domainFee.getAmount());
                assertThat(resultFractionalFee.getDenominator()).isEqualTo(domainFee.getAmountDenominator());
                assertThat(resultFractionalFee.getMaximumAmount()).isEqualTo(domainFee.getMaximumAmount());
                assertThat(resultFractionalFee.getMinimumAmount()).isEqualTo(domainFee.getMinimumAmount());
                assertThat(resultFractionalFee.getNetOfTransfers()).isEqualTo(domainFee.getNetOfTransfers());
                assertThat(resultFractionalFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void fixedFee() {
        var fixedFeeBuilder =
                FixedFee.builder().collectorAccountId(collectorId).denominatingTokenId(denominatingTokenId);
        var fixedFee = fixedFeeBuilder.amount(20L).build();
        var fixedFee2 = fixedFeeBuilder.amount(21L).build();
        var fixedFees = List.of(fixedFee, fixedFee2);
        customFee.setFixedFees(fixedFees);

        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId)).thenReturn(collectorAddress);

        var results = customFeeDatabaseAccessor.get(tokenId).get();
        var listAssert = assertThat(results).hasSize(2);
        for (var domainFee : fixedFees) {
            listAssert.anySatisfy(fee -> {
                assertThat(fee.getFractionalFee()).isNull();
                assertThat(fee.getRoyaltyFee()).isNull();
                var resultFixedFee = fee.getFixedFee();
                assertThat(resultFixedFee.getAmount()).isEqualTo(domainFee.getAmount());
                assertThat(resultFixedFee.isUseCurrentTokenForPayment()).isFalse();
                assertThat(resultFixedFee.getDenominatingTokenId()).isEqualTo(toAddress(denominatingTokenId));
                assertThat(resultFixedFee.isUseHbarsForPayment()).isFalse();
                assertThat(resultFixedFee.getFeeCollector()).isEqualTo(collectorAddress);
            });
        }
    }

    @Test
    void mapOnlyFeesWithCollectorAccountId() {
        final var noCollectorCustomFee = new com.hedera.mirror.common.domain.transaction.CustomFee();
        when(customFeeRepository.findById(tokenId)).thenReturn(Optional.of(noCollectorCustomFee));
        assertThat(customFeeDatabaseAccessor.get(tokenId))
                .hasValueSatisfying(customFees -> assertThat(customFees).hasSize(0));
    }
}
