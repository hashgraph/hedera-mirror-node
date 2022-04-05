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

import com.google.common.collect.Range;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;

class CryptoAdjustAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    private ArgumentCaptor<NftAllowance> nftAllowanceCaptor;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoAdjustAllowanceTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoAdjustAllowance(CryptoAdjustAllowanceTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransaction() {
        var recordItem = recordItemBuilder.cryptoAdjustAllowance().build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(recordItem, owner -> assertThat(owner).isPositive());
    }

    @Test
    void updateTransactionWithImplicitNftAllowanceOwner() {
        var recordItem = recordItemBuilder.cryptoAdjustAllowance()
                .transactionBody(body -> body.getNftAllowancesBuilderList().forEach(b -> b.clearOwner()))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        var effectiveNftOwner = recordItem.getPayerAccountId().getId();
        assertAllowances(recordItem, owner -> assertThat(owner).isEqualTo(effectiveNftOwner));
    }

    private void assertAllowances(RecordItem recordItem, Consumer<Long> assertNftOwner) {
        var timestamp = recordItem.getConsensusTimestamp();
        verify(entityListener).onCryptoAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertThat(a.getAmount()).isPositive())
                .satisfies(a -> assertThat(a.getOwner()).isPositive())
                .satisfies(a -> assertThat(a.getSpender()).isPositive())
                .returns(recordItem.getPayerAccountId(), CryptoAllowance::getPayerAccountId)
                .returns(timestamp, CryptoAllowance::getTimestampLower)));

        verify(entityListener, times(3)).onNftAllowance(nftAllowanceCaptor.capture());
        assertThat(nftAllowanceCaptor.getAllValues())
                .allSatisfy(n -> assertAll(
                        () -> assertNftOwner.accept(n.getOwner()),
                        () -> assertThat(n.getSpender()).isPositive(),
                        () -> assertThat(n.getTokenId()).isPositive(),
                        () -> assertThat(n.getPayerAccountId()).isEqualTo(recordItem.getPayerAccountId()),
                        () -> assertThat(n.getTimestampRange()).isEqualTo(Range.atLeast(timestamp))
                ))
                .extracting("approvedForAll")
                .containsExactlyInAnyOrder(true, false, true);

        verify(entityListener, times(4)).onNftInstanceAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertNftOwner.accept(a.getAccountId().getId()))
                .returns(timestamp, Nft::getAllowanceGrantedTimestamp)
                .returns(EntityId.EMPTY, Nft::getDelegatingSpender)
                .satisfies(a -> assertThat(a.getId().getSerialNumber()).isPositive())
                .satisfies(a -> assertThat(a.getSpender().getId()).isPositive())
                .satisfies(a -> assertThat(a.getId().getTokenId().getId()).isPositive())));

        verify(entityListener).onTokenAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertThat(a.getAmount()).isPositive())
                .satisfies(a -> assertThat(a.getOwner()).isPositive())
                .satisfies(a -> assertThat(a.getSpender()).isNotNull())
                .satisfies(a -> assertThat(a.getTokenId()).isPositive())
                .returns(recordItem.getPayerAccountId(), TokenAllowance::getPayerAccountId)
                .returns(timestamp, TokenAllowance::getTimestampLower)));
    }
}
