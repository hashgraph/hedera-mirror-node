package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FEE_NOT_SET;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.ROYALTY_FEE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.CustomFee;

class TokenFeeScheduleUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenFeeScheduleUpdateTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        var recordItem = recordItemBuilder.tokenFeeScheduleUpdate()
                .transactionBody(b -> b.setTokenId(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM).build()))
                .build();
        return TransactionBody.newBuilder()
                .setTokenFeeScheduleUpdate(recordItem.getTransactionBody().getTokenFeeScheduleUpdate());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @Test
    void updateTransactionFixedFee() {
        // Given
        var recordItem = recordItemBuilder.tokenFeeScheduleUpdate().build();
        var transaction = domainBuilder.transaction().get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var transactionBody = recordItem.getTransactionBody().getTokenFeeScheduleUpdate();
        var customFeeProto = transactionBody.getCustomFees(0);
        var fixedFee = customFeeProto.getFixedFee();
        long consensusTimestamp = transaction.getConsensusTimestamp();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());

        assertThat(customFee.getValue())
                .returns(customFeeProto.getAllCollectorsAreExempt(), CustomFee::isAllCollectorsAreExempt)
                .returns(fixedFee.getAmount(), CustomFee::getAmount)
                .returns(null, CustomFee::getAmountDenominator)
                .returns(EntityId.of(customFeeProto.getFeeCollectorAccountId()), CustomFee::getCollectorAccountId)
                .returns(EntityId.of(fixedFee.getDenominatingTokenId()), CustomFee::getDenominatingTokenId)
                .returns(null, CustomFee::getMaximumAmount)
                .returns(0L, CustomFee::getMinimumAmount)
                .returns(null, CustomFee::getNetOfTransfers)
                .returns(null, CustomFee::getRoyaltyDenominator)
                .returns(null, CustomFee::getRoyaltyNumerator)
                .returns(consensusTimestamp, c -> c.getId().getCreatedTimestamp())
                .returns(transaction.getEntityId(), c -> c.getId().getTokenId());
    }

    @Test
    void updateTransactionFractionalFee() {
        // Given
        var recordItem = recordItemBuilder.tokenFeeScheduleUpdate()
                .transactionBody(b -> b.clearCustomFees().addCustomFees(recordItemBuilder.customFee(FRACTIONAL_FEE)))
                .build();
        var transaction = domainBuilder.transaction().get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var transactionBody = recordItem.getTransactionBody().getTokenFeeScheduleUpdate();
        var customFeeProto = transactionBody.getCustomFees(0);
        var fractionalFee = customFeeProto.getFractionalFee();
        long consensusTimestamp = transaction.getConsensusTimestamp();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());

        assertThat(customFee.getValue())
                .returns(customFeeProto.getAllCollectorsAreExempt(), CustomFee::isAllCollectorsAreExempt)
                .returns(fractionalFee.getFractionalAmount().getNumerator(), CustomFee::getAmount)
                .returns(fractionalFee.getFractionalAmount().getDenominator(), CustomFee::getAmountDenominator)
                .returns(EntityId.of(customFeeProto.getFeeCollectorAccountId()), CustomFee::getCollectorAccountId)
                .returns(null, CustomFee::getDenominatingTokenId)
                .returns(fractionalFee.getMaximumAmount(), CustomFee::getMaximumAmount)
                .returns(fractionalFee.getMinimumAmount(), CustomFee::getMinimumAmount)
                .returns(fractionalFee.getNetOfTransfers(), CustomFee::getNetOfTransfers)
                .returns(null, CustomFee::getRoyaltyDenominator)
                .returns(null, CustomFee::getRoyaltyNumerator)
                .returns(consensusTimestamp, c -> c.getId().getCreatedTimestamp())
                .returns(transaction.getEntityId(), c -> c.getId().getTokenId());
    }

    @Test
    void updateTransactionRoyaltyFee() {
        // Given
        var recordItem = recordItemBuilder.tokenFeeScheduleUpdate()
                .transactionBody(b -> b.clearCustomFees().addCustomFees(recordItemBuilder.customFee(ROYALTY_FEE)))
                .build();
        var transaction = domainBuilder.transaction().get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var transactionBody = recordItem.getTransactionBody().getTokenFeeScheduleUpdate();
        var customFeeProto = transactionBody.getCustomFees(0);
        var royaltyFee = customFeeProto.getRoyaltyFee();
        var fallbackFee = royaltyFee.getFallbackFee();
        long consensusTimestamp = transaction.getConsensusTimestamp();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());

        assertThat(customFee.getValue())
                .returns(customFeeProto.getAllCollectorsAreExempt(), CustomFee::isAllCollectorsAreExempt)
                .returns(fallbackFee.getAmount(), CustomFee::getAmount)
                .returns(null, CustomFee::getAmountDenominator)
                .returns(EntityId.of(customFeeProto.getFeeCollectorAccountId()), CustomFee::getCollectorAccountId)
                .returns(EntityId.of(fallbackFee.getDenominatingTokenId()), CustomFee::getDenominatingTokenId)
                .returns(null, CustomFee::getMaximumAmount)
                .returns(0L, CustomFee::getMinimumAmount)
                .returns(null, CustomFee::getNetOfTransfers)
                .returns(royaltyFee.getExchangeValueFraction().getDenominator(), CustomFee::getRoyaltyDenominator)
                .returns(royaltyFee.getExchangeValueFraction().getNumerator(), CustomFee::getRoyaltyNumerator)
                .returns(consensusTimestamp, c -> c.getId().getCreatedTimestamp())
                .returns(transaction.getEntityId(), c -> c.getId().getTokenId());
    }

    @Test
    void updateTransactionEmpty() {
        // Given
        var recordItem = recordItemBuilder.tokenFeeScheduleUpdate().transactionBody(b -> b.clearCustomFees()).build();
        var transaction = domainBuilder.transaction().get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());

        assertThat(customFee.getValue())
                .returns(false, CustomFee::isAllCollectorsAreExempt)
                .returns(null, CustomFee::getAmount)
                .returns(null, CustomFee::getAmountDenominator)
                .returns(null, CustomFee::getCollectorAccountId)
                .returns(null, CustomFee::getDenominatingTokenId)
                .returns(null, CustomFee::getMaximumAmount)
                .returns(0L, CustomFee::getMinimumAmount)
                .returns(null, CustomFee::getNetOfTransfers)
                .returns(null, CustomFee::getRoyaltyDenominator)
                .returns(null, CustomFee::getRoyaltyNumerator)
                .returns(transaction.getConsensusTimestamp(), c -> c.getId().getCreatedTimestamp())
                .returns(transaction.getEntityId(), c -> c.getId().getTokenId());
    }

    @Test
    void updateTransactionUnknownFee() {
        // Given
        var recordItem = recordItemBuilder.tokenFeeScheduleUpdate()
                .transactionBody(b -> b.clearCustomFees().addCustomFees(recordItemBuilder.customFee(FEE_NOT_SET)))
                .build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenFeeScheduleUpdate().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener, never()).onCustomFee(any());
    }
}
