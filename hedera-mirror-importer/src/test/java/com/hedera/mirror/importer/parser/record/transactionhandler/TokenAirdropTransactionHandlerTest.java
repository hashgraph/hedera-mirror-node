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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.PendingAirdropValue;
import com.hederahashgraph.api.proto.java.TokenAirdropTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenAirdropTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenAirdropTransactionHandler(entityListener, entityProperties);
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
        entityProperties.getPersist().setTokenAirdrops(true);
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var tokenAirdrop = ArgumentCaptor.forClass(TokenAirdrop.class);
        long amount = 5L;
        var receiver1 = recordItemBuilder.accountId();
        var sender1 = recordItemBuilder.accountId();
        var token1 = recordItemBuilder.tokenId();
        var receiver2 = recordItemBuilder.accountId();
        var sender2 = recordItemBuilder.accountId();
        var token2 = recordItemBuilder.tokenId();
        var fungibleAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(receiver1)
                        .setSenderId(sender1)
                        .setFungibleTokenType(token1))
                .setPendingAirdropValue(
                        PendingAirdropValue.newBuilder().setAmount(amount).build());
        var nftAirdrop = PendingAirdropRecord.newBuilder()
                .setPendingAirdropId(PendingAirdropId.newBuilder()
                        .setReceiverId(receiver2)
                        .setSenderId(sender2)
                        .setNonFungibleToken(
                                NftID.newBuilder().setTokenID(token2).setSerialNumber(1L)));
        // TODO redo this with new pending
        var recordItem = recordItemBuilder
                .tokenAirdrop()
                .record(
                        r -> r.addNewPendingAirdrops(fungibleAirdrop)
                        //        .addNewPendingAirdrops(nftAirdrop)
                        )
                .build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();

        var expectedEntityTransactions = getExpectedEntityTransactions(
                recordItem,
                transaction,
                // EntityId.of(receiver1),
                // EntityId.of(receiver2),
                // EntityId.of(sender1),
                // EntityId.of(sender2),
                EntityId.of(token1),
                EntityId.of(token2));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        // assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);

        verify(entityListener).onTokenAirdrop(tokenAirdrop.capture());
        assertThat(tokenAirdrop.getValue())
                .returns(amount, TokenAirdrop::getAmount)
                .returns(receiver1.getAccountNum(), TokenAirdrop::getReceiverAccountId)
                .returns(sender1.getAccountNum(), TokenAirdrop::getSenderAccountId)
                .returns(Range.atLeast(timestamp), TokenAirdrop::getTimestampRange)
                .returns(token1.getTokenNum(), TokenAirdrop::getTokenId);
    }
}
