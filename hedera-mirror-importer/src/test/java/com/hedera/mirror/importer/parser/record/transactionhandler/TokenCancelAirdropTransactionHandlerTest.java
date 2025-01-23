/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.domain.token.TokenAirdropStateEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.TokenCancelAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

class TokenCancelAirdropTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EntityId receiver = domainBuilder.entityId();
    private final AccountID receiverAccountId = recordItemBuilder.accountId();
    private final EntityId sender = domainBuilder.entityId();
    private final AccountID senderAccountId = recordItemBuilder.accountId();

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenCancelAirdropTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenCancelAirdrop(
                        TokenCancelAirdropTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(receiverAccountId)).thenReturn(Optional.of(receiver));
        when(entityIdService.lookup(senderAccountId)).thenReturn(Optional.of(sender));
    }

    @ParameterizedTest
    @EnumSource(TokenTypeEnum.class)
    void cancelAirdrop(TokenTypeEnum tokenType) {
        // given
        var tokenAirdrop = ArgumentCaptor.forClass(TokenAirdrop.class);
        var token = recordItemBuilder.tokenId();
        var pendingAirdropId =
                PendingAirdropId.newBuilder().setReceiverId(receiverAccountId).setSenderId(senderAccountId);
        if (tokenType == TokenTypeEnum.FUNGIBLE_COMMON) {
            pendingAirdropId.setFungibleTokenType(token);
        } else {
            pendingAirdropId.setNonFungibleToken(
                    NftID.newBuilder().setTokenID(token).setSerialNumber(1L));
        }
        var recordItem = recordItemBuilder
                .tokenCancelAirdrop()
                .transactionBody(b -> b.clearPendingAirdrops().addPendingAirdrops(pendingAirdropId.build()))
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();

        var expectedEntityTransactions =
                getExpectedEntityTransactions(recordItem, transaction, receiver, sender, EntityId.of(token));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);

        verify(entityListener).onTokenAirdrop(tokenAirdrop.capture());
        assertThat(tokenAirdrop.getValue())
                .returns(receiver.getNum(), TokenAirdrop::getReceiverAccountId)
                .returns(sender.getNum(), TokenAirdrop::getSenderAccountId)
                .returns(TokenAirdropStateEnum.CANCELLED, TokenAirdrop::getState)
                .returns(Range.atLeast(timestamp), TokenAirdrop::getTimestampRange)
                .returns(token.getTokenNum(), TokenAirdrop::getTokenId);
        verifyNoMoreInteractions(entityListener);
    }
}
