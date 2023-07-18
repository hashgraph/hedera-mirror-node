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

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenWipeTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenWipeTransactionHandler(entityListener, entityProperties);
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
        // Given
        var recordItem = recordItemBuilder.tokenWipe(FUNGIBLE_COMMON).build();
        var body = recordItem.getTransactionBody().getTokenWipe();
        long timestamp = recordItem.getConsensusTimestamp();
        var tokenId = EntityId.of(body.getToken());
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(tokenId))
                .get();
        var token = ArgumentCaptor.forClass(Token.class);
        var accountId = EntityId.of(body.getAccount());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        verifyNoMoreInteractions(entityListener);

        assertThat(token.getValue())
                .returns(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(), Token::getTotalSupply)
                .returns(Range.atLeast(timestamp), Token::getTimestampRange)
                .returns(transaction.getEntityId().getId(), Token::getTokenId);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction, accountId));
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
        var accountId = EntityId.of(body.getAccount());

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        verify(entityListener).onNft(nft.capture());

        assertThat(token.getValue())
                .returns(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(), Token::getTotalSupply)
                .returns(Range.atLeast(timestamp), Token::getTimestampRange)
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
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
