/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.LiveHash;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CryptoAddLiveHashTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoAddLiveHashTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return recordItemBuilder
                .cryptoAddLiveHash()
                .transactionBody(
                        b -> b.getLiveHashBuilder().getAccountIdBuilder().setAccountNum(DEFAULT_ENTITY_NUM))
                .build()
                .getTransactionBody()
                .toBuilder();
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Test
    void updateTransaction() {
        // Given
        entityProperties.getPersist().setClaims(true);
        var recordItem = recordItemBuilder.cryptoAddLiveHash().build();
        var transaction = domainBuilder.transaction().get();
        var liveHash = ArgumentCaptor.forClass(LiveHash.class);
        var transactionBody = recordItem.getTransactionBody().getCryptoAddLiveHash();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onLiveHash(liveHash.capture());
        assertThat(liveHash.getValue())
                .returns(transaction.getConsensusTimestamp(), LiveHash::getConsensusTimestamp)
                .returns(transactionBody.getLiveHash().getHash().toByteArray(), LiveHash::getLivehash);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setClaims(false);
        var recordItem = recordItemBuilder.cryptoAddLiveHash().build();
        var transaction = domainBuilder.transaction().get();
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }
}
