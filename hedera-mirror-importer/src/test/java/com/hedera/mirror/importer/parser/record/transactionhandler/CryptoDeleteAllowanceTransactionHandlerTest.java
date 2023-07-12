/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

class CryptoDeleteAllowanceTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoDeleteAllowanceTransactionHandler(entityListener, syntheticContractLogService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoDeleteAllowance(
                        CryptoDeleteAllowanceTransactionBody.newBuilder().build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return null;
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder.cryptoDeleteAllowance().build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(timestamp);
    }

    @Test
    void updateTransactionSuccessfulWithImplicitOwner() {
        var recordItem = recordItemBuilder
                .cryptoDeleteAllowance()
                .transactionBody(b -> b.getNftAllowancesBuilderList().forEach(NftRemoveAllowance.Builder::clearOwner))
                .build();
        var effectiveOwner = recordItem.getPayerAccountId().getId();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(timestamp);
    }

    private void assertAllowances(long timestamp) {
        verify(entityListener, times(4)).onNft(assertArg(t -> assertThat(t)
                .isNotNull()
                .returns(null, Nft::getAccountId)
                .returns(null, Nft::getCreatedTimestamp)
                .returns(null, Nft::getDelegatingSpender)
                .returns(null, Nft::getDeleted)
                .returns(null, Nft::getMetadata)
                .satisfies(n -> assertThat(n.getId().getSerialNumber()).isPositive())
                .returns(null, Nft::getSpender)
                .returns(Range.atLeast(timestamp), Nft::getTimestampRange)
                .satisfies(n -> assertThat(n.getTokenId()).isPositive())));
    }
}
