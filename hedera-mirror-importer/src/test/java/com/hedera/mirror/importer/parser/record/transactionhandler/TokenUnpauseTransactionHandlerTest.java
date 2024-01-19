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
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.token.TokenPauseStatusEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TokenUnpauseTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenUnpauseTransactionHandler(entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setTokenUnpause(TokenUnpauseTransactionBody.newBuilder()
                        .setToken(TokenID.newBuilder().setTokenNum(DEFAULT_ENTITY_NUM)));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.TOKEN;
    }

    @Test
    void updateTransaction() {
        // Given
        var recordItem = recordItemBuilder.tokenUnpause().build();
        var transaction = domainBuilder.transaction().get();
        var token = ArgumentCaptor.forClass(Token.class);

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verify(entityListener).onToken(token.capture());

        assertThat(token.getValue())
                .returns(TokenPauseStatusEnum.UNPAUSED, Token::getPauseStatus)
                .returns(recordItem.getConsensusTimestamp(), Token::getTimestampLower)
                .returns(transaction.getEntityId().getId(), Token::getTokenId);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }

    @Test
    void updateTransactionDisabled() {
        // Given
        entityProperties.getPersist().setTokens(false);
        var recordItem = recordItemBuilder.tokenUnpause().build();
        var transaction = domainBuilder.transaction().get();

        // When
        transactionHandler.updateTransaction(transaction, recordItem);

        // Then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(recordItem, transaction));
    }
}
