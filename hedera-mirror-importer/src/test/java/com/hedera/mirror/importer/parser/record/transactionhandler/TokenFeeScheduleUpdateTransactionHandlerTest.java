/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FEE_NOT_SET;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.FRACTIONAL_FEE;
import static com.hederahashgraph.api.proto.java.CustomFee.FeeCase.ROYALTY_FEE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.CustomFee;
import com.hedera.mirror.common.domain.token.FixedFee;
import com.hedera.mirror.common.domain.token.FractionalFee;
import com.hedera.mirror.common.domain.token.RoyaltyFee;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenFeeScheduleUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenFeeScheduleUpdateTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        var recordItem = recordItemBuilder
                .tokenFeeScheduleUpdate()
                .transactionBody(b -> b.setTokenId(
                        TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM).build()))
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
        long timestamp = recordItem.getConsensusTimestamp();
        var body = recordItem.getTransactionBody().getTokenFeeScheduleUpdate();
        var tokenId = EntityId.of(body.getTokenId());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var customFeeProto = body.getCustomFees(0);
        var fixedFee = customFeeProto.getFixedFee();
        var feeCollectorId = EntityId.of(customFeeProto.getFeeCollectorAccountId());
        var feeTokenId = EntityId.of(customFeeProto.getFixedFee().getDenominatingTokenId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());

        assertThat(customFee.getValue())
                .returns(Range.atLeast(transaction.getConsensusTimestamp()), CustomFee::getTimestampRange)
                .returns(transaction.getEntityId().getId(), CustomFee::getTokenId)
                .returns(null, CustomFee::getFractionalFees)
                .returns(null, CustomFee::getRoyaltyFees);
        var listAssert =
                Assertions.assertThat(customFee.getValue().getFixedFees()).hasSize(1);
        listAssert.extracting(FixedFee::getAmount).containsOnly(fixedFee.getAmount());
        listAssert
                .extracting(FixedFee::getCollectorAccountId)
                .containsOnly(EntityId.of(customFeeProto.getFeeCollectorAccountId()));
        listAssert
                .extracting(FixedFee::isAllCollectorsAreExempt)
                .containsOnly(customFeeProto.getAllCollectorsAreExempt());
        listAssert
                .extracting(FixedFee::getDenominatingTokenId)
                .containsOnly(EntityId.of(customFeeProto.getFixedFee().getDenominatingTokenId()));

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, feeCollectorId, feeTokenId));
    }

    @Test
    void updateTransactionFractionalFee() {
        // Given
        var customFeeProto = recordItemBuilder.customFee(FRACTIONAL_FEE).build();
        var recordItem = recordItemBuilder
                .tokenFeeScheduleUpdate()
                .transactionBody(b -> b.clearCustomFees().addCustomFees(customFeeProto))
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var body = recordItem.getTransactionBody().getTokenFeeScheduleUpdate();
        var tokenId = EntityId.of(body.getTokenId());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var feeCollectorId = EntityId.of(customFeeProto.getFeeCollectorAccountId());
        var fractionalFee = customFeeProto.getFractionalFee();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());

        assertThat(customFee.getValue())
                .returns(Range.atLeast(transaction.getConsensusTimestamp()), CustomFee::getTimestampRange)
                .returns(transaction.getEntityId().getId(), CustomFee::getTokenId)
                .returns(null, CustomFee::getFixedFees)
                .returns(null, CustomFee::getRoyaltyFees);
        var listAssert =
                Assertions.assertThat(customFee.getValue().getFractionalFees()).hasSize(1);
        listAssert
                .extracting(FractionalFee::getNumerator)
                .containsOnly(
                        customFeeProto.getFractionalFee().getFractionalAmount().getNumerator());
        listAssert
                .extracting(FractionalFee::getCollectorAccountId)
                .containsOnly(EntityId.of(customFeeProto.getFeeCollectorAccountId()));
        listAssert
                .extracting(FractionalFee::isAllCollectorsAreExempt)
                .containsOnly(customFeeProto.getAllCollectorsAreExempt());
        listAssert
                .extracting(FractionalFee::getDenominator)
                .containsOnly(fractionalFee.getFractionalAmount().getDenominator());
        listAssert.extracting(FractionalFee::getMaximumAmount).containsOnly(fractionalFee.getMaximumAmount());
        listAssert.extracting(FractionalFee::getMinimumAmount).containsOnly(fractionalFee.getMinimumAmount());
        listAssert.extracting(FractionalFee::isNetOfTransfers).containsOnly(fractionalFee.getNetOfTransfers());

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, feeCollectorId));
    }

    @Test
    void updateTransactionRoyaltyFee() {
        // Given
        var customFeeProto = recordItemBuilder.customFee(ROYALTY_FEE).build();
        var recordItem = recordItemBuilder
                .tokenFeeScheduleUpdate()
                .transactionBody(b -> b.clearCustomFees().addCustomFees(customFeeProto))
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var body = recordItem.getTransactionBody().getTokenFeeScheduleUpdate();
        var tokenId = EntityId.of(body.getTokenId());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var royaltyFee = customFeeProto.getRoyaltyFee();
        long consensusTimestamp = transaction.getConsensusTimestamp();
        var fallbackFee = royaltyFee.getFallbackFee();
        var feeCollectorId = EntityId.of(customFeeProto.getFeeCollectorAccountId());
        var feeTokenId = EntityId.of(fallbackFee.getDenominatingTokenId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());
        var capturedCustomFee = customFee.getValue();
        assertThat(capturedCustomFee)
                .returns(Range.atLeast(consensusTimestamp), CustomFee::getTimestampRange)
                .returns(transaction.getEntityId().getId(), CustomFee::getTokenId)
                .returns(null, CustomFee::getFixedFees)
                .returns(null, CustomFee::getFractionalFees);
        var listAssert =
                Assertions.assertThat(capturedCustomFee.getRoyaltyFees()).hasSize(1);
        listAssert
                .extracting(RoyaltyFee::getCollectorAccountId)
                .containsOnly(EntityId.of(customFeeProto.getFeeCollectorAccountId()));
        listAssert
                .extracting(RoyaltyFee::isAllCollectorsAreExempt)
                .containsOnly(customFeeProto.getAllCollectorsAreExempt());
        listAssert
                .extracting(RoyaltyFee::getNumerator)
                .containsOnly(royaltyFee.getExchangeValueFraction().getNumerator());
        listAssert
                .extracting(RoyaltyFee::getDenominator)
                .containsOnly(royaltyFee.getExchangeValueFraction().getDenominator());
        var capturedFallbackFee = capturedCustomFee.getRoyaltyFees().get(0).getFallbackFee();
        assertThat(capturedFallbackFee.getAmount())
                .isEqualTo(royaltyFee.getFallbackFee().getAmount());
        assertThat(capturedFallbackFee.getDenominatingTokenId().getId())
                .isEqualTo(royaltyFee.getFallbackFee().getDenominatingTokenId().getTokenNum());

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, feeCollectorId, feeTokenId));
    }

    @Test
    void updateTransactionRoyaltyFeeNoFallbackFee() {
        // Given
        var royaltyFeeProto = recordItemBuilder.royaltyFee().clearFallbackFee();
        var customFeeProto = recordItemBuilder
                .customFee(ROYALTY_FEE)
                .setRoyaltyFee(royaltyFeeProto)
                .build();
        var recordItem = recordItemBuilder
                .tokenFeeScheduleUpdate()
                .transactionBody(b -> b.clearCustomFees().addCustomFees(customFeeProto))
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var body = recordItem.getTransactionBody().getTokenFeeScheduleUpdate();
        var tokenId = EntityId.of(body.getTokenId());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var feeCollectorId = EntityId.of(customFeeProto.getFeeCollectorAccountId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());
        var capturedCustomFee = customFee.getValue();
        assertThat(capturedCustomFee.getRoyaltyFees().get(0).getFallbackFee()).isNull();

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(recordItem, transaction, feeCollectorId));
    }

    @Test
    void updateTransactionEmpty() {
        // Given
        var recordItem = recordItemBuilder
                .tokenFeeScheduleUpdate()
                .transactionBody(b -> b.clearCustomFees())
                .build();
        var transaction = domainBuilder.transaction().get();
        long consensusTimestamp = transaction.getConsensusTimestamp();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onCustomFee(customFee.capture());

        assertThat(customFee.getValue())
                .returns(null, CustomFee::getFixedFees)
                .returns(null, CustomFee::getFractionalFees)
                .returns(null, CustomFee::getRoyaltyFees)
                .returns(Range.atLeast(consensusTimestamp), CustomFee::getTimestampRange)
                .returns(transaction.getEntityId().getId(), CustomFee::getTokenId);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionUnknownFee() {
        // Given
        var recordItem = recordItemBuilder
                .tokenFeeScheduleUpdate()
                .transactionBody(b -> b.clearCustomFees().addCustomFees(recordItemBuilder.customFee(FEE_NOT_SET)))
                .build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
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
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
