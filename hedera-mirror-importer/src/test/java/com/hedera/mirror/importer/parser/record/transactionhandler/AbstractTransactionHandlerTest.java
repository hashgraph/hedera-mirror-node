package com.hedera.mirror.importer.parser.record.transactionhandler;

/*
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Fail.fail;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.shaded.org.bouncycastle.util.Strings;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public abstract class AbstractTransactionHandlerTest {
    private static final Long DEFAULT_ENTITY_NUM = 100L;

    private TransactionHandler transactionHandler;

    protected abstract TransactionHandler getTransactionHandler();
    // Map from field name to entity type
    protected abstract Map<String, Integer> getEntityIdFields();

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        System.out.println("Before test: " + testInfo.getTestMethod().get().getName());
        transactionHandler = getTransactionHandler();
    }

    @Test
    void testNullEntityId() {
        assertExpectedEntityId(TransactionBody.newBuilder(), TransactionRecord.newBuilder(), null);
    }

    // Tests that TransactionHandler.getEntityId() returns expected entity id.
    // Entity id is set using protocol buffer reflection.
    @Test
    void testGetEntityId() {
        // given
        Message.Builder transactionBodyBuilder = TransactionBody.newBuilder();
        Message.Builder transactionRecordBuilder = TransactionRecord.newBuilder();
        Map<String, Integer> entityIdFields = getEntityIdFields();
        for (var entityIdField : entityIdFields.entrySet()) {
            Iterator<String> fields = Arrays.stream(Strings.split(entityIdField.getKey(), '.')).iterator();
            String fieldName = fields.next();
            if (fieldName.equals("body")) {
                setFieldsRecursively(transactionBodyBuilder, fields);
            } else if (fieldName.equals("record")) {
                setFieldsRecursively(transactionRecordBuilder, fields);
            } else {
                fail("Bad field name " + fieldName);
            }
            EntityId expectedEntityId = new EntityId(null, 0L, 0L, DEFAULT_ENTITY_NUM, entityIdField.getValue());

            // then
            assertExpectedEntityId(transactionBodyBuilder, transactionRecordBuilder, expectedEntityId);
        }
    }

    // Recursively populates the next field in 'remainingFields'.
    // If no more fields are left in iterator, then current field has to be account/file/contract/topic id.
    private static void setFieldsRecursively(
            Message.Builder parentBuilder, Iterator<String> remainingFields) {
        if (!remainingFields.hasNext()) {
            // set entity num to non-zero value
            Descriptors.Descriptor descriptor = parentBuilder.getDescriptorForType();
            if (descriptor == AccountID.getDescriptor()) {
                ((AccountID.Builder) parentBuilder).setAccountNum(DEFAULT_ENTITY_NUM);
            } else if (descriptor == ContractID.getDescriptor()) {
                ((ContractID.Builder) parentBuilder).setContractNum(DEFAULT_ENTITY_NUM);
            } else if (descriptor == FileID.getDescriptor()) {
                ((FileID.Builder) parentBuilder).setFileNum(DEFAULT_ENTITY_NUM);
            } else if (descriptor == TopicID.getDescriptor()) {
                ((TopicID.Builder) parentBuilder).setTopicNum(DEFAULT_ENTITY_NUM);
            } else {
                fail("field is not of type account/contract/file/topic: " + parentBuilder);
            }
        } else {
            Descriptors.FieldDescriptor fieldDescriptor =
                    parentBuilder.getDescriptorForType().findFieldByName(remainingFields.next());
            Message.Builder fieldBuilder = parentBuilder.newBuilderForField(fieldDescriptor);
            setFieldsRecursively(fieldBuilder, remainingFields);
            parentBuilder.setField(fieldDescriptor, fieldBuilder.build());
        }
    }

    // Asserts that recordItem built using given transaction body and record will return 'expectedEntityId' on
    // TransactionHandler.getEntityId().
    private void assertExpectedEntityId(Message.Builder txBodyBuilder, Message.Builder txRecordBuilder,
                                        EntityId expectedEntityId) {
        RecordItem recordItem = new RecordItem(
                Transaction.newBuilder().setBodyBytes(txBodyBuilder.build().toByteString()).build(),
                (TransactionRecord) txRecordBuilder.build());
        if (expectedEntityId == null) {
            assertThat(transactionHandler.getEntityId(recordItem)).isNull();
        } else {
            assertThat(transactionHandler.getEntityId(recordItem)).isEqualTo(expectedEntityId);
        }
    }
}
