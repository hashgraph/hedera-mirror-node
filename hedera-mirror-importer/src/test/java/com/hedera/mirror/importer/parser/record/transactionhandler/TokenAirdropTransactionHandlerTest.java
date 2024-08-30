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

import static com.hedera.mirror.common.domain.token.TokenAirdropStateEnum.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.util.DomainUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.TokenAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenAirdropTransactionHandlerTest extends AbstractTransactionHandlerTest {
    private final AccountID receiverAlias = AccountID.newBuilder()
            .setAlias(DomainUtils.fromBytes(domainBuilder.evmAddress()))
            .build();
    private final EntityId receiver = domainBuilder.entityId();
    private final AccountID senderAlias = AccountID.newBuilder()
            .setAlias(DomainUtils.fromBytes(domainBuilder.evmAddress()))
            .build();
    private final EntityId sender = domainBuilder.entityId();

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenAirdropTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenAirdrop(TokenAirdropTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(receiverAlias)).thenReturn(Optional.of(receiver));
        when(entityIdService.lookup(senderAlias)).thenReturn(Optional.of(sender));
    }

    @Test
    void updateTransactionSuccessfulFungiblePendingAirdrop() {
        // given
        var tokenAirdrop = ArgumentCaptor.forClass(TokenAirdrop.class);
        long amount = 5L;
        var token = recordItemBuilder.tokenId();
        var fungibleAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(receiverAlias)
                        .setSenderId(senderAlias)
                        .setFungibleTokenType(token))
                .setPendingAirdropValue(
                        PendingAirdropValue.newBuilder().setAmount(amount).build());
        var recordItem = recordItemBuilder
                .tokenAirdrop()
                .record(r -> r.clearNewPendingAirdrops().addNewPendingAirdrops(fungibleAirdrop))
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
                .returns(amount, TokenAirdrop::getAmount)
                .returns(receiver.getNum(), TokenAirdrop::getReceiverAccountId)
                .returns(sender.getNum(), TokenAirdrop::getSenderAccountId)
                .returns(0L, TokenAirdrop::getSerialNumber)
                .returns(PENDING, TokenAirdrop::getState)
                .returns(Range.atLeast(timestamp), TokenAirdrop::getTimestampRange)
                .returns(token.getTokenNum(), TokenAirdrop::getTokenId);
    }

    @Test
    void updateTransactionSuccessfulNftPendingAirdrop() {
        // given
        var tokenAirdrop = ArgumentCaptor.forClass(TokenAirdrop.class);
        var token = recordItemBuilder.tokenId();
        var nftAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(receiverAlias)
                        .setSenderId(senderAlias)
                        .setNonFungibleToken(
                                NftID.newBuilder().setTokenID(token).setSerialNumber(1L)));
        var recordItem = recordItemBuilder
                .tokenAirdrop()
                .record(r -> r.clearNewPendingAirdrops().addNewPendingAirdrops(nftAirdrop))
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
                .returns(null, TokenAirdrop::getAmount)
                .returns(receiver.getNum(), TokenAirdrop::getReceiverAccountId)
                .returns(sender.getNum(), TokenAirdrop::getSenderAccountId)
                .returns(PENDING, TokenAirdrop::getState)
                .returns(Range.atLeast(timestamp), TokenAirdrop::getTimestampRange)
                .returns(token.getTokenNum(), TokenAirdrop::getTokenId);
    }
}
