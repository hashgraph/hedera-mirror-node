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
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.repository.CustomFeeRepository;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import java.util.List;
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
        customFee.setCollectorAccountId(collectorId);
    }

    @Test
    void royaltyFee() {
        long denominator = 10L;
        long numerator = 15L;
        long amount = 20L;

        customFee.setRoyaltyDenominator(denominator);
        customFee.setRoyaltyNumerator(numerator);
        customFee.setAmount(amount);
        customFee.setDenominatingTokenId(denominatingTokenId);

        when(customFeeRepository.findByTokenId(tokenId)).thenReturn(singletonList(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId)).thenReturn(collectorAddress);

        assertThat(customFeeDatabaseAccessor.get(tokenId)).hasValueSatisfying(customFees -> assertThat(customFees)
                .hasSize(1)
                .first()
                .returns(null, CustomFee::getFixedFee)
                .returns(null, CustomFee::getFractionalFee)
                .returns(denominator, f -> f.getRoyaltyFee().getDenominator())
                .returns(numerator, f -> f.getRoyaltyFee().getNumerator())
                .returns(amount, f -> f.getRoyaltyFee().getAmount())
                .returns(toAddress(denominatingTokenId), f -> f.getRoyaltyFee().getDenominatingTokenId())
                .returns(false, f -> f.getRoyaltyFee().isUseHbarsForPayment())
                .returns(collectorAddress, f -> f.getRoyaltyFee().getFeeCollector()));
    }

    @Test
    void fractionFee() {
        long denominator = 0;
        long numerator = 0;
        long amount = 20L;
        long amountDenominator = 2L;
        long maxAmount = 100L;
        long minAmount = 5L;

        customFee.setRoyaltyDenominator(denominator);
        customFee.setRoyaltyNumerator(numerator);
        customFee.setAmountDenominator(amountDenominator);
        customFee.setAmount(amount);
        customFee.setMaximumAmount(maxAmount);
        customFee.setMinimumAmount(minAmount);
        customFee.setNetOfTransfers(true);

        when(customFeeRepository.findByTokenId(tokenId)).thenReturn(singletonList(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId)).thenReturn(collectorAddress);

        assertThat(customFeeDatabaseAccessor.get(tokenId)).hasValueSatisfying(customFees -> assertThat(customFees)
                .hasSize(1)
                .first()
                .returns(null, CustomFee::getFixedFee)
                .returns(null, CustomFee::getRoyaltyFee)
                .returns(amount, f -> f.getFractionalFee().getNumerator())
                .returns(amountDenominator, f -> f.getFractionalFee().getDenominator())
                .returns(maxAmount, f -> f.getFractionalFee().getMaximumAmount())
                .returns(minAmount, f -> f.getFractionalFee().getMinimumAmount())
                .returns(true, f -> f.getFractionalFee().getNetOfTransfers())
                .returns(collectorAddress, f -> f.getFractionalFee().getFeeCollector()));
    }

    @Test
    void fixedFee() {
        long denominator = 0;
        long numerator = 0;
        long amount = 20L;
        long amountDenominator = 0;

        customFee.setRoyaltyDenominator(denominator);
        customFee.setRoyaltyNumerator(numerator);
        customFee.setAmountDenominator(amountDenominator);
        customFee.setAmount(amount);
        customFee.setDenominatingTokenId(denominatingTokenId);

        when(customFeeRepository.findByTokenId(tokenId)).thenReturn(singletonList(customFee));
        when(entityDatabaseAccessor.evmAddressFromId(collectorId)).thenReturn(collectorAddress);

        assertThat(customFeeDatabaseAccessor.get(tokenId)).hasValueSatisfying(customFees -> assertThat(customFees)
                .hasSize(1)
                .first()
                .returns(null, CustomFee::getFractionalFee)
                .returns(null, CustomFee::getRoyaltyFee)
                .returns(amount, f -> f.getFixedFee().getAmount())
                .returns(false, f -> f.getFixedFee().isUseCurrentTokenForPayment())
                .returns(toAddress(denominatingTokenId), f -> f.getFixedFee().getDenominatingTokenId())
                .returns(false, f -> f.getFixedFee().isUseHbarsForPayment())
                .returns(collectorAddress, f -> f.getFixedFee().getFeeCollector()));
    }

    @Test
    void mapOnlyFeesWithCollectorAccountId() {
        final var noCollectorCustomFee = new com.hedera.mirror.common.domain.transaction.CustomFee();
        noCollectorCustomFee.setCollectorAccountId(null);

        when(customFeeRepository.findByTokenId(tokenId)).thenReturn(List.of(customFee, noCollectorCustomFee));

        assertThat(customFeeDatabaseAccessor.get(tokenId))
                .hasValueSatisfying(customFees -> assertThat(customFees).hasSize(1));
    }
}
