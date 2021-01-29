package com.hedera.mirror.importer.parser.record.transactionhandler;

/*
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;

public abstract class AbstractTransactionHandlerTest {
    protected static final Long DEFAULT_ENTITY_NUM = 100L;

    private TransactionHandler transactionHandler;

    protected abstract TransactionHandler getTransactionHandler();

    // All sub-classes need to implement this function and return a TransactionBody.Builder with valid 'oneof data' set.
    protected abstract TransactionBody.Builder getDefaultTransactionBody();

    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder();
    }

    // For testGetEntityId
    protected abstract EntityTypeEnum getExpectedEntityIdType();

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        System.out.println("Before test: " + testInfo.getTestMethod().get().getName());
        transactionHandler = getTransactionHandler();
    }

    @Test
    void testGetEntityId() {
        EntityId expectedEntityId = null;
        var entityType = getExpectedEntityIdType();
        if (entityType != null) {
            expectedEntityId = EntityId.of(0L, 0L, DEFAULT_ENTITY_NUM, entityType);
        }
        testGetEntityIdHelper(getDefaultTransactionBody().build(), getDefaultTransactionRecord().build(),
                expectedEntityId);
    }

    protected void testGetEntityIdHelper(
            TransactionBody transactionBody, TransactionRecord transactionRecord, EntityId expectedEntity) {
        RecordItem recordItem = new RecordItem(
                Transaction.newBuilder().
                        setSignedTransactionBytes(SignedTransaction.newBuilder()
                                .setBodyBytes(transactionBody.toByteString())
                                .build().toByteString())
                        .build(),
                transactionRecord);
        assertThat(transactionHandler.getEntity(recordItem)).isEqualTo(expectedEntity);
    }
}
