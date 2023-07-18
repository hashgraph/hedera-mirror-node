/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hedera.mirror.common.domain.token.TokenSupplyTypeEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.common.domain.transaction.CustomFee;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord.Builder;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class TokenCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        var feeScheduleHandler = new TokenFeeScheduleUpdateTransactionHandler(entityListener, entityProperties);
        return new TokenCreateTransactionHandler(entityIdService, entityListener, entityProperties, feeScheduleHandler);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        var recordItem = recordItemBuilder.tokenCreate().build();
        return TransactionBody.newBuilder()
                .setTokenCreation(recordItem.getTransactionBody().getTokenCreation());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder()
                .setStatus(responseCodeEnum)
                .setTokenID(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM).build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.tokenCreate().build();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var token = ArgumentCaptor.forClass(Token.class);
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var transactionBody = recordItem.getTransactionBody().getTokenCreation();
        var customFeeProto = transactionBody.getCustomFees(0);
        var customFeeCollector = EntityId.of(customFeeProto.getFeeCollectorAccountId());
        var customFeeTokenId = EntityId.of(customFeeProto.getFixedFee().getDenominatingTokenId());
        var autoAssociation = recordItem.getTransactionRecord().getAutomaticTokenAssociations(0);
        var expectedEntityTransactions = getExpectedEntityTransactions(
                recordItem,
                transaction,
                customFeeCollector,
                customFeeTokenId,
                EntityId.of(autoAssociation.getAccountId()));

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onCustomFee(customFee.capture());
        verify(entityListener).onTokenAccount(tokenAccount.capture());
        assertThat(token.getValue())
                .returns(timestamp, Token::getCreatedTimestamp)
                .returns(transactionBody.getDecimals(), Token::getDecimals)
                .returns(transactionBody.getFeeScheduleKey().toByteArray(), Token::getFeeScheduleKey)
                .returns(transactionBody.getFreezeKey().toByteArray(), Token::getFreezeKey)
                .returns(transactionBody.getFreezeDefault(), Token::getFreezeDefault)
                .returns(transactionBody.getInitialSupply(), Token::getInitialSupply)
                .returns(transactionBody.getKycKey().toByteArray(), Token::getKycKey)
                .returns(transactionBody.getMaxSupply(), Token::getMaxSupply)
                .returns(Range.atLeast(timestamp), Token::getTimestampRange)
                .returns(transactionBody.getName(), Token::getName)
                .returns(transactionBody.getPauseKey().toByteArray(), Token::getPauseKey)
                .returns(TokenPauseStatusEnum.UNPAUSED, Token::getPauseStatus)
                .returns(transactionBody.getSupplyKey().toByteArray(), Token::getSupplyKey)
                .returns(TokenSupplyTypeEnum.fromId(transactionBody.getSupplyTypeValue()), Token::getSupplyType)
                .returns(transactionBody.getSymbol(), Token::getSymbol)
                .returns(transactionBody.getInitialSupply(), Token::getTotalSupply)
                .returns(EntityId.of(transactionBody.getTreasury()), Token::getTreasuryAccountId)
                .returns(TokenTypeEnum.fromId(transactionBody.getTokenTypeValue()), Token::getType)
                .returns(transaction.getEntityId().getId(), Token::getTokenId)
                .returns(transactionBody.getWipeKey().toByteArray(), Token::getWipeKey);

        assertThat(customFee.getValue())
                .returns(customFeeProto.getAllCollectorsAreExempt(), CustomFee::isAllCollectorsAreExempt)
                .returns(customFeeProto.getFixedFee().getAmount(), CustomFee::getAmount)
                .returns(customFeeCollector, CustomFee::getCollectorAccountId)
                .returns(customFeeTokenId, CustomFee::getDenominatingTokenId)
                .returns(null, CustomFee::getMaximumAmount)
                .returns(0L, CustomFee::getMinimumAmount)
                .returns(null, CustomFee::getNetOfTransfers)
                .returns(null, CustomFee::getRoyaltyDenominator)
                .returns(null, CustomFee::getRoyaltyNumerator)
                .returns(timestamp, c -> c.getId().getCreatedTimestamp())
                .returns(transaction.getEntityId(), c -> c.getId().getTokenId());

        assertThat(tokenAccount.getValue())
                .returns(EntityId.of(autoAssociation.getAccountId()).getId(), TokenAccount::getAccountId)
                .returns(true, TokenAccount::getAssociated)
                .returns(false, TokenAccount::getAutomaticAssociation)
                .returns(timestamp, TokenAccount::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus)
                .returns(Range.atLeast(timestamp), TokenAccount::getTimestampRange)
                .returns(transaction.getEntityId().getId(), TokenAccount::getTokenId);

        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionMinimal() {
        // Given
        var recordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> b.clearAutoRenewAccount()
                        .clearAdminKey()
                        .clearAutoRenewPeriod()
                        .clearCustomFees()
                        .clearExpiry()
                        .clearFeeScheduleKey()
                        .clearFreezeKey()
                        .clearKycKey()
                        .clearMemo()
                        .clearPauseKey()
                        .clearSupplyKey()
                        .clearWipeKey())
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
        var treasuryId =
                EntityId.of(recordItem.getTransactionBody().getTokenCreation().getTreasury());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var token = ArgumentCaptor.forClass(Token.class);
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var expectedEntity = tokenId.toEntity().toBuilder()
                .deleted(false)
                .createdTimestamp(timestamp)
                .memo("")
                .timestampRange(Range.atLeast(timestamp))
                .build();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onEntity(ArgumentMatchers.assertArg(e -> assertEquals(expectedEntity, e)));
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onCustomFee(any());
        verify(entityListener).onTokenAccount(tokenAccount.capture());

        assertThat(token.getValue())
                .returns(null, Token::getFeeScheduleKey)
                .returns(null, Token::getFreezeKey)
                .returns(null, Token::getKycKey)
                .returns(null, Token::getPauseKey)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(null, Token::getSupplyKey)
                .returns(null, Token::getWipeKey);

        assertThat(tokenAccount.getValue())
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, TokenAccount::getKycStatus);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction, treasuryId));
    }

    @Test
    void updateTransactionNoAutoAssociations() {
        // Given
        var recordItem = recordItemBuilder
                .tokenCreate()
                .transactionBody(b -> {
                    // 0.0.0 as denominating token id means fee is charged in the newly created token
                    var customFee = b.getCustomFees(0).toBuilder();
                    customFee.setFixedFee(
                            customFee.getFixedFeeBuilder().setDenominatingTokenId(TokenID.getDefaultInstance()));
                    b.setCustomFees(0, customFee);
                })
                .record(Builder::clearAutomaticTokenAssociations)
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getTokenID());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var transactionBody = recordItem.getTransactionBody().getTokenCreation();
        var customFeeProto = transactionBody.getCustomFees(0);
        var customFeeCollectorId = EntityId.of(customFeeProto.getFeeCollectorAccountId());
        transaction.setType(TransactionType.TOKENCREATION.getProtoId());
        var expectedEntityTransactions = getExpectedEntityTransactions(
                recordItem, transaction, customFeeCollectorId, EntityId.of(transactionBody.getTreasury()));

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(any());
        verify(entityListener).onCustomFee(any());
        verify(entityListener, times(2)).onTokenAccount(tokenAccount.capture());

        assertThat(tokenAccount.getAllValues())
                .asInstanceOf(InstanceOfAssertFactories.list(TokenAccount.class))
                .extracting(TokenAccount::getAccountId)
                .containsExactlyInAnyOrder(
                        EntityId.of(customFeeProto.getFeeCollectorAccountId()).getId(),
                        EntityId.of(transactionBody.getTreasury()).getId());

        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenCreate().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener, never()).onToken(any());
        verify(entityListener, never()).onTokenAccount(any());
        verify(entityListener, never()).onCustomFee(any());
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
