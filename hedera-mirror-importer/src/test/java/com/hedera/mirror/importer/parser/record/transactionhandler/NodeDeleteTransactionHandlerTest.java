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
import com.hederahashgraph.api.proto.java.NodeDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import org.junit.jupiter.api.Test;

class NodeDeleteTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new NodeDeleteTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setNodeDelete(NodeDeleteTransactionBody.newBuilder().setNodeId(DEFAULT_ENTITY_NUM));
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder()
                .setStatus(responseCodeEnum)
                .setAccountID(
                        AccountID.newBuilder().setAccountNum(DEFAULT_ENTITY_NUM).build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Test
    void nodeDeleteTransaction() {
        // given
        var recordItem = recordItemBuilder.nodeDelete().build();
        var transaction = domainBuilder.transaction().get();

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertThat(recordItem.getTransactionBody().getNodeDelete().toByteArray())
                .containsExactly(transaction.getTransactionBytes());
        assertThat(recordItem.getTransactionRecord().toByteArray())
                .containsExactly(transaction.getTransactionRecordBytes());
    }
}
