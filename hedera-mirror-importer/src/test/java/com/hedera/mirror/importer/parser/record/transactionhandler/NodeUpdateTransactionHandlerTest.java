/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.EntityType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NodeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NodeUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeUpdateTransactionHandler(entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setNodeUpdate(NodeUpdateTransactionBody.newBuilder()
                        .setAccountId(AccountID.newBuilder()
                                .setAccountNum(DEFAULT_ENTITY_NUM)
                                .build())
                        .build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @AfterEach
    void after() {
        entityProperties.getPersist().setNodes(false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nodeUpdateTransaction(boolean isPersist) {

        entityProperties.getPersist().setNodes(isPersist);

        // given
        var recordItem = recordItemBuilder.nodeUpdate().build();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.transactionBytes(null).transactionRecordBytes(null))
                .get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        var transactionBytes = isPersist ? recordItem.getTransaction().toByteArray() : null;
        var transactionRecordBytes =
                isPersist ? recordItem.getTransactionRecord().toByteArray() : null;
        // then
        assertThat(transaction.getTransactionBytes()).containsExactly(transactionBytes);
        assertThat(transaction.getTransactionRecordBytes()).containsExactly(transactionRecordBytes);
    }
}
