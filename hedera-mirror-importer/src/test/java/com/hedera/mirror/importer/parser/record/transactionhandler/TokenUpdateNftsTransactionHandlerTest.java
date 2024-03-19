/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.AbstractNft;
import com.hedera.mirror.common.domain.token.Nft;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateNftsTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateNftsTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenUpdateNftsTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenUpdateNftsTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenUpdateNfts(TokenUpdateNftsTransactionBody.newBuilder()
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
    void updateNftMetadata() {
        // Given
        var recordItem = recordItemBuilder.tokenUpdateNfts().build();
        var transaction = domainBuilder.transaction().get();
        var nft = ArgumentCaptor.forClass(Nft.class);
        var expectedSerialNumbers = List.of(1L, 2L);
        var expectedNfts = expectedSerialNumbers.size();
        var expectedMetadata = recordItem
                .getTransactionBody()
                .getTokenUpdateNfts()
                .getMetadata()
                .getValue()
                .toByteArray();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener, times(expectedNfts)).onNft(nft.capture());
        verifyNoMoreInteractions(entityListener);

        var nfts = assertThat(nft.getAllValues()).hasSize(expectedNfts);
        for (int i = 0; i < expectedNfts; i++) {
            nfts.element(i)
                    .isNotNull()
                    .returns(expectedMetadata, Nft::getMetadata)
                    .returns(expectedSerialNumbers.get(i), AbstractNft::getSerialNumber)
                    .returns(Range.atLeast(recordItem.getConsensusTimestamp()), Nft::getTimestampRange)
                    .returns(transaction.getEntityId().getId(), AbstractNft::getTokenId);
        }

        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateNftHasMetadataFalse() {
        // Given
        var recordItem = recordItemBuilder
                .tokenUpdateNfts()
                .transactionBody(Builder::clearMetadata)
                .build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateNftTransactionNotSuccessful() {
        // Given
        var recordItem = recordItemBuilder
                .tokenUpdateNfts()
                .status(ResponseCodeEnum.INVALID_TRANSACTION)
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
        var recordItem = recordItemBuilder.tokenUpdateNfts().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
