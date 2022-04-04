package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.common.collect.Range;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;

class CryptoApproveAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    private ArgumentCaptor<NftAllowance> nftAllowanceCaptor;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoApproveAllowanceTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionUnsuccessful() {
        var transaction = new Transaction();
        RecordItem recordItem = recordItemBuilder.cryptoApproveAllowance()
                .receipt(r -> r.setStatus(ResponseCodeEnum.ACCOUNT_DELETED))
                .build();
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder.cryptoApproveAllowance().build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();
        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onCryptoAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertThat(a.getAmount()).isPositive())
                .satisfies(a -> assertThat(a.getOwner()).isPositive())
                .satisfies(a -> assertThat(a.getSpender()).isPositive())
                .returns(recordItem.getPayerAccountId(), CryptoAllowance::getPayerAccountId)
                .returns(timestamp, CryptoAllowance::getTimestampLower)));

        verify(entityListener, times(2)).onNftAllowance(nftAllowanceCaptor.capture());
        assertThat(nftAllowanceCaptor.getAllValues())
                .allSatisfy(n -> assertAll(
                        () -> assertThat(n.getOwner()).isPositive(),
                        () -> assertThat(n.getSpender()).isPositive(),
                        () -> assertThat(n.getTokenId()).isPositive(),
                        () -> assertThat(n.getPayerAccountId()).isEqualTo(recordItem.getPayerAccountId()),
                        () -> assertThat(n.getTimestampRange()).isEqualTo(Range.atLeast(timestamp))
                ))
                .extracting("approvedForAll").
                containsExactlyInAnyOrder(true, false);

        verify(entityListener, times(2)).onNftInstanceAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertThat(a.getAccountId()).isNotNull())
                .returns(timestamp, Nft::getAllowanceGrantedTimestamp)
                .satisfies(a -> assertThat(a.getDelegatingSpender()).isEqualTo(EntityId.EMPTY))
                .satisfies(a -> assertThat(a.getId().getSerialNumber()).isPositive())
                .satisfies(a -> assertThat(a.getId().getTokenId()).isNotNull())
                .satisfies(a -> assertThat(a.getSpender()).isNotNull())));

        verify(entityListener).onTokenAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertThat(a.getAmount()).isPositive())
                .satisfies(a -> assertThat(a.getOwner()).isPositive())
                .satisfies(a -> assertThat(a.getSpender()).isNotNull())
                .satisfies(a -> assertThat(a.getTokenId()).isPositive())
                .returns(recordItem.getPayerAccountId(), TokenAllowance::getPayerAccountId)
                .returns(timestamp, TokenAllowance::getTimestampLower)));
    }

    @Test
    void updateTransactionSuccessfulWithImplicitOwner() {
        var recordItem = recordItemBuilder.cryptoApproveAllowance().transactionBody(b -> {
            b.getCryptoAllowancesBuilderList().forEach(builder -> builder.clearOwner());
            b.getNftAllowancesBuilderList().forEach(builder -> builder.clearOwner());
            b.getTokenAllowancesBuilderList().forEach(builder -> builder.clearOwner());
        }).build();
        var effectiveOwner = recordItem.getPayerAccountId();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();
        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onCryptoAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertThat(a.getAmount()).isPositive())
                .returns(effectiveOwner.getId(), CryptoAllowance::getOwner)
                .satisfies(a -> assertThat(a.getSpender()).isPositive())
                .returns(recordItem.getPayerAccountId(), CryptoAllowance::getPayerAccountId)
                .returns(timestamp, CryptoAllowance::getTimestampLower)));

        verify(entityListener, times(2)).onNftAllowance(nftAllowanceCaptor.capture());
        assertThat(nftAllowanceCaptor.getAllValues())
                .allSatisfy(n -> assertAll(
                        () -> assertThat(n.getOwner()).isEqualTo(effectiveOwner.getId()),
                        () -> assertThat(n.getSpender()).isPositive(),
                        () -> assertThat(n.getTokenId()).isPositive(),
                        () -> assertThat(n.getPayerAccountId()).isEqualTo(recordItem.getPayerAccountId()),
                        () -> assertThat(n.getTimestampRange()).isEqualTo(Range.atLeast(timestamp))
                        ))
                .extracting("approvedForAll").
                containsExactlyInAnyOrder(true, false);

        verify(entityListener, times(2)).onNftInstanceAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(effectiveOwner, Nft::getAccountId)
                .returns(timestamp, Nft::getAllowanceGrantedTimestamp)
                .satisfies(a -> assertThat(a.getDelegatingSpender()).isEqualTo(EntityId.EMPTY))
                .satisfies(a -> assertThat(a.getId().getSerialNumber()).isPositive())
                .satisfies(a -> assertThat(a.getId().getTokenId()).isNotNull())
                .satisfies(a -> assertThat(a.getSpender()).isNotNull())));

        verify(entityListener).onTokenAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertThat(a.getAmount()).isPositive())
                .returns(effectiveOwner.getId(), TokenAllowance::getOwner)
                .satisfies(a -> assertThat(a.getSpender()).isNotNull())
                .satisfies(a -> assertThat(a.getTokenId()).isPositive())
                .returns(recordItem.getPayerAccountId(), TokenAllowance::getPayerAccountId)
                .returns(timestamp, TokenAllowance::getTimestampLower)));
    }
}
