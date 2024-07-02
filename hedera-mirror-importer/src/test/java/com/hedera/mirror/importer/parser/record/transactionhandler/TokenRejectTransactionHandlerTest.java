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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenRejectTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TokenRejectTransactionHandlerTest extends AbstractTransactionHandlerTest {
    @Override
    protected TransactionHandler getTransactionHandler() {
        return new TokenRejectTransactionHandler(entityIdService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        var ownerId = AccountID.newBuilder().setAccountNum(DEFAULT_ENTITY_NUM).build();
        return TransactionBody.newBuilder()
                .setTokenReject(TokenRejectTransactionBody.newBuilder()
                        .setOwner(ownerId)
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @BeforeEach
    void beforeEach() {
        var ownerId = EntityId.of(DEFAULT_ENTITY_NUM);
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.of(ownerId));
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.tokenReject().build();
        long timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp))
                .get();
        var rejections = recordItem.getTransactionBody().getTokenReject().getRejectionsList().stream()
                .map(rejection -> {
                    var tokenId = rejection.hasFungibleToken()
                            ? rejection.getFungibleToken()
                            : rejection.getNft().getTokenID();
                    return EntityId.of(tokenId);
                })
                .toArray(EntityId[]::new);
        var expectedEntityTransactions = getExpectedEntityTransactions(recordItem, transaction, rejections);

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        verifyNoInteractions(entityListener);
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @ParameterizedTest
    @MethodSource("provideOwners")
    void updateTransactionNoOwner(Optional<EntityId> ownerId) {
        var recordItem = recordItemBuilder.tokenReject().build();
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(ownerId);
        testGetEntityIdHelper(
                recordItem.getTransactionBody(), recordItem.getTransactionRecord(), recordItem.getPayerAccountId());
    }

    private static Stream<Arguments> provideOwners() {
        return Stream.of(Arguments.of(Optional.of(EntityId.EMPTY)), Arguments.of(Optional.empty()));
    }
}
