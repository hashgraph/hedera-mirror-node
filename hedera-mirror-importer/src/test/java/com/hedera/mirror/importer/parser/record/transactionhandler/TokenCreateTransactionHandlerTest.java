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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum)
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
        var transaction = domainBuilder.transaction().get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var token = ArgumentCaptor.forClass(Token.class);
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var transactionBody = recordItem.getTransactionBody().getTokenCreation();
        var customFeeProto = transactionBody.getCustomFees(0);
        var autoAssociation = recordItem.getTransactionRecord().getAutomaticTokenAssociations(0);
        long consensusTimestamp = transaction.getConsensusTimestamp();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onCustomFee(customFee.capture());
        verify(entityListener).onTokenAccount(tokenAccount.capture());
        assertThat(token.getValue())
                .returns(consensusTimestamp, Token::getCreatedTimestamp)
                .returns(transactionBody.getDecimals(), Token::getDecimals)
                .returns(transactionBody.getFeeScheduleKey().toByteArray(), Token::getFeeScheduleKey)
                .returns(transactionBody.getFreezeKey().toByteArray(), Token::getFreezeKey)
                .returns(transactionBody.getFreezeDefault(), Token::getFreezeDefault)
                .returns(transactionBody.getInitialSupply(), Token::getInitialSupply)
                .returns(transactionBody.getKycKey().toByteArray(), Token::getKycKey)
                .returns(transactionBody.getMaxSupply(), Token::getMaxSupply)
                .returns(consensusTimestamp, Token::getModifiedTimestamp)
                .returns(transactionBody.getName(), Token::getName)
                .returns(transactionBody.getPauseKey().toByteArray(), Token::getPauseKey)
                .returns(TokenPauseStatusEnum.UNPAUSED, Token::getPauseStatus)
                .returns(transactionBody.getSupplyKey().toByteArray(), Token::getSupplyKey)
                .returns(TokenSupplyTypeEnum.fromId(transactionBody.getSupplyTypeValue()), Token::getSupplyType)
                .returns(transactionBody.getSymbol(), Token::getSymbol)
                .returns(transactionBody.getInitialSupply(), Token::getTotalSupply)
                .returns(EntityId.of(transactionBody.getTreasury()), Token::getTreasuryAccountId)
                .returns(TokenTypeEnum.fromId(transactionBody.getTokenTypeValue()), Token::getType)
                .returns(transaction.getEntityId(), t -> t.getTokenId().getTokenId())
                .returns(transactionBody.getWipeKey().toByteArray(), Token::getWipeKey);

        assertThat(customFee.getValue())
                .returns(customFeeProto.getAllCollectorsAreExempt(), CustomFee::isAllCollectorsAreExempt)
                .returns(customFeeProto.getFixedFee().getAmount(), CustomFee::getAmount)
                .returns(EntityId.of(customFeeProto.getFeeCollectorAccountId()), CustomFee::getCollectorAccountId)
                .returns(EntityId.of(customFeeProto.getFixedFee()
                        .getDenominatingTokenId()), CustomFee::getDenominatingTokenId)
                .returns(null, CustomFee::getMaximumAmount)
                .returns(0L, CustomFee::getMinimumAmount)
                .returns(null, CustomFee::getNetOfTransfers)
                .returns(null, CustomFee::getRoyaltyDenominator)
                .returns(null, CustomFee::getRoyaltyNumerator)
                .returns(consensusTimestamp, c -> c.getId().getCreatedTimestamp())
                .returns(transaction.getEntityId(), c -> c.getId().getTokenId());

        assertThat(tokenAccount.getValue())
                .returns(EntityId.of(autoAssociation.getAccountId()).getId(), TokenAccount::getAccountId)
                .returns(true, TokenAccount::getAssociated)
                .returns(false, TokenAccount::getAutomaticAssociation)
                .returns(consensusTimestamp, TokenAccount::getCreatedTimestamp)
                .returns(TokenFreezeStatusEnum.UNFROZEN, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.GRANTED, TokenAccount::getKycStatus)
                .returns(consensusTimestamp, TokenAccount::getTimestampLower)
                .returns(null, TokenAccount::getTimestampUpper)
                .returns(transaction.getEntityId().getId(), TokenAccount::getTokenId);
    }

    @Test
    void updateTransactionEmptyFields() {
        // Given
        var recordItem = recordItemBuilder.tokenCreate()
                .transactionBody(b -> b.clearCustomFees()
                        .clearFeeScheduleKey()
                        .clearFreezeKey()
                        .clearKycKey()
                        .clearPauseKey()
                        .clearSupplyKey()
                        .clearWipeKey())
                .build();
        var transaction = domainBuilder.transaction().get();
        var customFee = ArgumentCaptor.forClass(CustomFee.class);
        var token = ArgumentCaptor.forClass(Token.class);
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onCustomFee(customFee.capture());
        verify(entityListener).onTokenAccount(tokenAccount.capture());

        assertThat(token.getValue())
                .returns(null, Token::getFeeScheduleKey)
                .returns(null, Token::getFreezeKey)
                .returns(null, Token::getKycKey)
                .returns(null, Token::getPauseKey)
                .returns(TokenPauseStatusEnum.NOT_APPLICABLE, Token::getPauseStatus)
                .returns(null, Token::getSupplyKey)
                .returns(null, Token::getWipeKey);

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

        assertThat(tokenAccount.getValue())
                .returns(TokenFreezeStatusEnum.NOT_APPLICABLE, TokenAccount::getFreezeStatus)
                .returns(TokenKycStatusEnum.NOT_APPLICABLE, TokenAccount::getKycStatus);
    }

    @Test
    void updateTransactionNoAutoAssociations() {
        // Given
        var recordItem = recordItemBuilder.tokenCreate().record(r -> r.clearAutomaticTokenAssociations()).build();
        var transaction = domainBuilder.transaction().get();
        var tokenAccount = ArgumentCaptor.forClass(TokenAccount.class);
        var transactionBody = recordItem.getTransactionBody().getTokenCreation();
        var customFeeProto = transactionBody.getCustomFees(0);
        transaction.setEntityId(EntityId.of(customFeeProto.getFixedFee().getDenominatingTokenId()));
        transaction.setType(TransactionType.TOKENCREATION.getProtoId());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(any());
        verify(entityListener).onCustomFee(any());
        verify(entityListener, times(2)).onTokenAccount(tokenAccount.capture());

        assertThat(tokenAccount.getAllValues())
                .asInstanceOf(InstanceOfAssertFactories.list(TokenAccount.class))
                .extracting(TokenAccount::getAccountId)
                .containsExactlyInAnyOrder(EntityId.of(customFeeProto.getFeeCollectorAccountId()).getId(),
                        EntityId.of(transactionBody.getTreasury()).getId());
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
    }
}
