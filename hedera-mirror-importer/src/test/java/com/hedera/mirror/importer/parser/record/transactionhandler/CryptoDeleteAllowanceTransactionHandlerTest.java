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

import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;

class CryptoDeleteAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoDeleteAllowanceTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionUnsuccessful() {
        var transaction = new Transaction();
        RecordItem recordItem = recordItemBuilder.cryptoDeleteAllowance()
                .receipt(r -> r.setStatus(ResponseCodeEnum.ACCOUNT_DELETED))
                .build();
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder.cryptoDeleteAllowance().build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(owner -> assertThat(owner).isPositive());
    }

    @Test
    void updateTransactionSuccessfulWithImplicitOwner() {
        var recordItem = recordItemBuilder.cryptoDeleteAllowance().transactionBody(b -> b
                .getNftAllowancesBuilderList().forEach(NftRemoveAllowance.Builder::clearOwner)).build();
        var effectiveOwner = recordItem.getPayerAccountId().getId();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().customize(t -> t.consensusTimestamp(timestamp)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(owner -> assertThat(owner).isEqualTo(effectiveOwner));
    }

    private void assertAllowances(Consumer<Long> assertOwner) {
        verify(entityListener, times(4)).onNft(assertArg(t -> assertThat(t)
                .isNotNull()
                .satisfies(n -> assertOwner.accept(n.getAccountId().getId()))
                .returns(null, Nft::getAllowanceGrantedTimestamp)
                .returns(null, Nft::getCreatedTimestamp)
                .returns(null, Nft::getDelegatingSpender)
                .returns(null, Nft::getDeleted)
                .returns(null, Nft::getMetadata)
                .returns(null, Nft::getModifiedTimestamp)
                .satisfies(n -> assertThat(n.getId().getSerialNumber()).isPositive())
                .returns(null, Nft::getSpender)
                .satisfies(n -> assertThat(n.getId().getTokenId().getId()).isPositive())));
    }
}
