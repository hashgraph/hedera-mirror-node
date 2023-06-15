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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.AbstractNft;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenMintTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenMintTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenMint(TokenMintTransactionBody.newBuilder()
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
        var recordItem = recordItemBuilder.tokenMint(FUNGIBLE_COMMON).build();
        var transaction = domainBuilder.transaction().get();
        var token = ArgumentCaptor.forClass(Token.class);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        verifyNoMoreInteractions(entityListener);

        assertThat(token.getValue())
                .returns(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(), Token::getTotalSupply)
                .returns(recordItem.getConsensusTimestamp(), Token::getModifiedTimestamp)
                .returns(transaction.getEntityId(), t -> t.getTokenId().getTokenId());
    }

    @Test
    void updateTransactionNonFungible() {
        // Given
        var recordItem = recordItemBuilder.tokenMint(NON_FUNGIBLE_UNIQUE).build();
        var transaction = domainBuilder.transaction().get();
        var nft = ArgumentCaptor.forClass(Nft.class);
        var token = ArgumentCaptor.forClass(Token.class);
        var transactionBody = recordItem.getTransactionBody().getTokenMint();
        var receipt = recordItem.getTransactionRecord().getReceipt();
        int expectedNfts = transactionBody.getMetadataCount();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());
        verify(entityListener, times(expectedNfts)).onNft(nft.capture());

        assertThat(token.getValue())
                .returns(recordItem.getTransactionRecord().getReceipt().getNewTotalSupply(), Token::getTotalSupply)
                .returns(recordItem.getConsensusTimestamp(), Token::getModifiedTimestamp)
                .returns(transaction.getEntityId(), t -> t.getTokenId().getTokenId());

        var nfts = assertThat(nft.getAllValues()).hasSize(expectedNfts);
        for (int i = 0; i < expectedNfts; i++) {
            nfts.element(i)
                    .isNotNull()
                    .returns(recordItem.getConsensusTimestamp(), Nft::getCreatedTimestamp)
                    .returns(false, Nft::getDeleted)
                    .returns(transactionBody.getMetadata(i).toByteArray(), Nft::getMetadata)
                    .returns(receipt.getSerialNumbers(i), AbstractNft::getSerialNumber)
                    .returns(Range.atLeast(recordItem.getConsensusTimestamp()), Nft::getTimestampRange)
                    .returns(transaction.getEntityId().getId(), AbstractNft::getTokenId);
        }
    }

    @Test
    void updateTransactionNonFungibleMismatch() {
        // Given
        var recordItem = recordItemBuilder
                .tokenMint(NON_FUNGIBLE_UNIQUE)
                .receipt(r -> r.addSerialNumbers(3L))
                .build();
        var transaction = domainBuilder.transaction().get();
        var nft = ArgumentCaptor.forClass(Nft.class);
        var transactionBody = recordItem.getTransactionBody().getTokenMint();
        int expectedNfts = transactionBody.getMetadataCount();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(any());
        verify(entityListener, times(expectedNfts)).onNft(nft.capture());

        assertThat(nft.getAllValues())
                .hasSize(expectedNfts)
                .extracting(Nft::getSerialNumber)
                .containsExactly(1L, 2L);
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenMint().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
    }
}
