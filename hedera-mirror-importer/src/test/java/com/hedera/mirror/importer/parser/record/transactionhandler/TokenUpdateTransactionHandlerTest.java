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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.util.DomainUtils;

@ExtendWith(MockitoExtension.class)
class TokenUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final static long DEFAULT_AUTO_RENEW_ACCOUNT_NUM = 2;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenUpdateTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenUpdate(TokenUpdateTransactionBody.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM))
                        .setAdminKey(DEFAULT_KEY)
                        .setExpiry(Timestamp.newBuilder().setSeconds(360))
                        .setKycKey(DEFAULT_KEY)
                        .setFreezeKey(DEFAULT_KEY)
                        .setSymbol("SYMBOL")
                        .setTreasury(AccountID.newBuilder().setAccountNum(1))
                        .setAutoRenewAccount(AccountID.newBuilder().setAccountNum(DEFAULT_AUTO_RENEW_ACCOUNT_NUM))
                        .setAutoRenewPeriod(Duration.newBuilder().setSeconds(100))
                        .setName("token_name")
                        .setWipeKey(DEFAULT_KEY));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @Test
    void updateTransactionSuccessful() {
        // Given
        var recordItem = recordItemBuilder.tokenUpdate().build();
        var tokenId = EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var transactionBody = recordItem.getTransactionBody().getTokenUpdate();
        var timestamp = recordItem.getConsensusTimestamp();
        var token = ArgumentCaptor.forClass(Token.class);
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId)).get();
        when(entityIdService.lookup(any(AccountID.class)))
                .thenReturn(Optional.of(EntityIdEndec.decode(10, EntityType.ACCOUNT)));

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        assertTokenUpdate(timestamp, tokenId, id -> assertEquals(10L, id));
        verify(entityListener).onToken(token.capture());
        assertThat(token.getValue())
                .returns(transactionBody.getFeeScheduleKey().toByteArray(), Token::getFeeScheduleKey)
                .returns(transactionBody.getFreezeKey().toByteArray(), Token::getFreezeKey)
                .returns(transactionBody.getKycKey().toByteArray(), Token::getKycKey)
                .returns(timestamp, Token::getModifiedTimestamp)
                .returns(transactionBody.getName(), Token::getName)
                .returns(transactionBody.getPauseKey().toByteArray(), Token::getPauseKey)
                .returns(transactionBody.getSupplyKey().toByteArray(), Token::getSupplyKey)
                .returns(transactionBody.getSymbol(), Token::getSymbol)
                .returns(EntityId.of(transactionBody.getTreasury()), Token::getTreasuryAccountId)
                .returns(transaction.getEntityId(), t -> t.getTokenId().getTokenId())
                .returns(transactionBody.getWipeKey().toByteArray(), Token::getWipeKey);
    }

    @Test
    void updateTransactionMinimal() {
        // Given
        var recordItem = recordItemBuilder.tokenUpdate()
                .transactionBody(b -> b.clearFeeScheduleKey()
                        .clearFreezeKey()
                        .clearKycKey()
                        .clearName()
                        .clearPauseKey()
                        .clearSupplyKey()
                        .clearSymbol()
                        .clearTreasury()
                        .clearWipeKey())
                .build();
        var token = ArgumentCaptor.forClass(Token.class);
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        assertThat(token.getValue())
                .returns(null, Token::getFeeScheduleKey)
                .returns(null, Token::getFreezeKey)
                .returns(recordItem.getConsensusTimestamp(), Token::getModifiedTimestamp)
                .returns(null, Token::getName)
                .returns(null, Token::getPauseKey)
                .returns(null, Token::getSupplyKey)
                .returns(null, Token::getSymbol)
                .returns(null, Token::getTreasuryAccountId)
                .returns(transaction.getEntityId(), t -> t.getTokenId().getTokenId())
                .returns(null, Token::getWipeKey);
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenUpdate().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener, never()).onToken(any());
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.tokenUpdate()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAlias(alias))
                .build();
        var tokenId = EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.of(EntityIdEndec.decode(10L, ACCOUNT)));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertTokenUpdate(timestamp, tokenId, id -> assertEquals(10L, id));
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionWithEmptyEntity(EntityId entityId) {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.tokenUpdate()
                .transactionBody(b -> b.getAutoRenewAccountBuilder().setAlias(alias))
                .build();
        var tokenId = EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.ofNullable(entityId));
        var expectedId = entityId == null ? null : entityId.getId();

        transactionHandler.updateTransaction(transaction, recordItem);

        assertTokenUpdate(timestamp, tokenId, id -> assertEquals(expectedId, id));
    }

    @SuppressWarnings("java:S6103")
    void assertTokenUpdate(long timestamp, EntityId tokenId, Consumer<Long> assertAutoRenewAccountId) {
        verify(entityListener).onEntity(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(e -> assertAutoRenewAccountId.accept(e.getAutoRenewAccountId()))
                .satisfies(e -> assertThat(e.getAutoRenewPeriod()).isPositive())
                .returns(null, Entity::getCreatedTimestamp)
                .returns(false, Entity::getDeleted)
                .satisfies(e -> assertThat(e.getExpirationTimestamp()).isPositive())
                .returns(tokenId.getId(), Entity::getId)
                .satisfies(e -> assertThat(e.getKey()).isNotEmpty())
                .returns(null, Entity::getMaxAutomaticTokenAssociations)
                .satisfies(e -> assertThat(e.getMemo()).isNotEmpty())
                .returns(tokenId.getEntityNum(), Entity::getNum)
                .returns(null, Entity::getProxyAccountId)
                .satisfies(e -> assertThat(e.getPublicKey()).isNotEmpty())
                .returns(tokenId.getRealmNum(), Entity::getRealm)
                .returns(tokenId.getShardNum(), Entity::getShard)
                .returns(EntityType.TOKEN, Entity::getType)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)));
    }
}
