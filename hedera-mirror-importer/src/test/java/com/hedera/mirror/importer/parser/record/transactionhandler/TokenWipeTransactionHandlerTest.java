/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class TokenWipeTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenWipeTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenWipe(TokenWipeAccountTransactionBody.newBuilder()
                        .setAccount(AccountID.newBuilder().setAccountNum(DEFAULT_ENTITY_NUM))
                        .setToken(TokenID.newBuilder()
                                .setTokenNum(DEFAULT_ENTITY_NUM)
                                .build())
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @Test
    void updateTransactionFungible() {
        var accountId = recordItemBuilder.accountId();
        var resolved = Optional.of(EntityId.of(accountId));
        testUpdateTransactionFungible(accountId, resolved);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateTransactionFungibleWithAliasAccount(boolean isResolvable) {
        var accountId = recordItemBuilder.accountId();
        var aliasAccountId =
                AccountID.newBuilder().setAlias(recordItemBuilder.bytes(32)).build();
        var resolved = isResolvable ? Optional.of(EntityId.of(accountId)) : Optional.<EntityId>empty();
        testUpdateTransactionFungible(aliasAccountId, resolved);
    }

    @Test
    void updateTransactionNonFungible() {
        // Given
        var recordItem = recordItemBuilder.tokenWipe(NON_FUNGIBLE_UNIQUE).build();
        var body = recordItem.getTransactionBody().getTokenWipe();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(body.getToken());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var nft = ArgumentCaptor.forClass(Nft.class);
        var token = ArgumentCaptor.forClass(Token.class);
        var protoAccountId = body.getAccount();
        var accountId = EntityId.of(protoAccountId);
        when(entityIdService.lookup(protoAccountId)).thenReturn(Optional.of(accountId));

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityIdService).lookup(protoAccountId);
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onNft(nft.capture());

        assertThat(token.getValue())
                .returns(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(), Token::getTotalSupply)
                .returns(transaction.getEntityId().getId(), Token::getTokenId);

        assertThat(nft.getValue())
                .returns(true, Nft::getDeleted)
                .returns(body.getSerialNumbers(0), Nft::getSerialNumber)
                .returns(Range.atLeast(timestamp), Nft::getTimestampRange)
                .returns(tokenId.getId(), Nft::getTokenId);

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction, accountId));
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenWipe().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityIdService);
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    void testUpdateTransactionFungible(AccountID accountId, Optional<EntityId> resolved) {
        // Given
        var recordItem = recordItemBuilder
                .tokenWipe(FUNGIBLE_COMMON)
                .transactionBody(b -> b.setAccount(accountId))
                .build();
        var body = recordItem.getTransactionBody().getTokenWipe();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(body.getToken());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var token = ArgumentCaptor.forClass(Token.class);
        when(entityIdService.lookup(accountId)).thenReturn(resolved);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityIdService).lookup(accountId);
        verify(entityListener).onToken(token.capture());
        verifyNoMoreInteractions(entityListener);

        assertThat(token.getValue())
                .returns(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(), Token::getTotalSupply)
                .returns(transaction.getEntityId().getId(), Token::getTokenId);
        var expected = resolved.isPresent()
                ? getExpectedEntityTransactions(recordItem, transaction, resolved.get())
                : getExpectedEntityTransactions(recordItem, transaction);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expected);
    }
}
