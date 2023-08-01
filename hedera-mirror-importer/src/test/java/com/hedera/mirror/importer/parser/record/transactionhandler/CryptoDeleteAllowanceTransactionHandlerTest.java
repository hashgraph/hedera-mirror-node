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
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.stream.Stream;
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
                .customize(t -> t.consensusTimestamp(timestamp).entityId(null))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(timestamp);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulWithImplicitOwner() {
        var recordItem = recordItemBuilder
                .cryptoDeleteAllowance()
                .transactionBody(b -> b.getNftAllowancesBuilderList().forEach(NftRemoveAllowance.Builder::clearOwner))
                .build();
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(null))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertAllowances(timestamp);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
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

    private Map<Long, EntityTransaction> getExpectedEntityTransactions(RecordItem recordItem, Transaction transaction) {
        var body = recordItem.getTransactionBody().getCryptoDeleteAllowance();
        var payerAccountId = recordItem.getPayerAccountId();
        var entityIds = body.getNftAllowancesList().stream().flatMap(allowance -> {
            var owner = allowance.getOwner().equals(AccountID.getDefaultInstance())
                    ? payerAccountId
                    : EntityId.of(allowance.getOwner());
            return Stream.of(owner, EntityId.of(allowance.getTokenId()));
        });
        return getExpectedEntityTransactions(recordItem, transaction, entityIds.toArray(EntityId[]::new));
    }
}
