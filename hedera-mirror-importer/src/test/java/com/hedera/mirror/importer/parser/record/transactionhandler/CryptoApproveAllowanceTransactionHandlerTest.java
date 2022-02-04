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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;

class CryptoApproveAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

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

        verify(entityListener, times(2)).onNftAllowance(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(a -> assertThat(a.isApprovedForAll()).isNotNull())
                .satisfies(a -> assertThat(a.getOwner()).isPositive())
                .satisfies(a -> assertThat(a.getSpender()).isPositive())
                .satisfies(a -> assertThat(a.getTokenId()).isPositive())
                .returns(recordItem.getPayerAccountId(), NftAllowance::getPayerAccountId)
                .returns(timestamp, NftAllowance::getTimestampLower)));

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
