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

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.StringValue;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInfo;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

public abstract class AbstractTransactionHandlerTest {

    protected static final Duration DEFAULT_AUTO_RENEW_PERIOD = Duration.newBuilder().setSeconds(1).build();

    protected static final Long DEFAULT_CREATED_TIMESTAMP = 0L;

    protected static final boolean DEFAULT_DELETED = false;

    protected static final Long DEFAULT_ENTITY_NUM = 100L;

    protected static final Timestamp DEFAULT_EXPIRATION_TIME = Utility.instantToTimestamp(Instant.now());

    protected static final Key DEFAULT_KEY = getKey("4a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96f");

    protected static final String DEFAULT_MEMO = "default entity memo";

    protected static final Key DEFAULT_SUBMIT_KEY = getKey(
            "5a5ad514f0957fa170a676210c9bdbddf3bc9519702cf915fa6767a40463b96G");

    protected static final KeyList DEFAULT_KEY_LIST = KeyList.newBuilder().addAllKeys(
            Arrays.asList(DEFAULT_KEY, DEFAULT_SUBMIT_KEY))
            .build();

    protected static final String UPDATED_MEMO = "update memo";

    private TransactionHandler transactionHandler;

    protected abstract TransactionHandler getTransactionHandler();

    // All sub-classes need to implement this function and return a TransactionBody.Builder with valid 'oneof data' set.
    protected abstract TransactionBody.Builder getDefaultTransactionBody();

    protected SignatureMap.Builder getDefaultSigMap() {
        return SignatureMap.newBuilder();
    }

    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder();
    }

    // For testGetEntityId
    protected abstract EntityTypeEnum getExpectedEntityIdType();

    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecs() {
        return null;
    }

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

    @TestFactory
    Stream<DynamicTest> testUpdateEntity() {
        if (!transactionHandler.updatesEntity()) {
            // empty test if the handler does not update entity
            return Stream.empty();
        }

        FieldDescriptor memoField = getInnerBodyFieldDescriptorByName("memo");
        FieldDescriptor memoWrapperField = getInnerBodyFieldDescriptorByName("memoWrapper");
        List<UpdateEntityTestSpec> testSpecs;

        if (memoField != null) {
            // it's either an entity create transaction or entity update transaction when memo field is present
            boolean isMemoString = memoField.getType() == FieldDescriptor.Type.STRING;

            if (isMemoString && memoWrapperField == null) {
                testSpecs = getUpdateEntityTestSpecsForCreateTransaction(memoField);
            } else {
                testSpecs = getUpdateEntityTestSpecsForUpdateTransaction(memoField, memoWrapperField);
            }
        } else {
            // no memo field, either delete or undelete transaction, leave it to the test class
            testSpecs = getUpdateEntityTestSpecs();
        }

        return DynamicTest.stream(
                testSpecs.iterator(),
                UpdateEntityTestSpec::getDescription,
                (testSpec) -> {
                    // given spec
                    Entity actual = testSpec.getInput();

                    // when
                    transactionHandler.updateEntity(actual, testSpec.getRecordItem());

                    // then
                    assertThat(actual).isEqualTo(testSpec.getExpected());
                }
        );
    }

    protected void testGetEntityIdHelper(
            TransactionBody transactionBody, TransactionRecord transactionRecord, EntityId expectedEntity) {
        RecordItem recordItem = new RecordItem(
                Transaction.newBuilder().
                        setSignedTransactionBytes(SignedTransaction.newBuilder()
                                .setBodyBytes(transactionBody.toByteString())
                                .setSigMap(getDefaultSigMap())
                                .build().toByteString())
                        .build(),
                transactionRecord);
        assertThat(transactionHandler.getEntity(recordItem)).isEqualTo(expectedEntity);
    }

    private List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForCreateTransaction(FieldDescriptor memoField) {
        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        List<UpdateEntityTestSpec> testSpecs = new LinkedList<>();

        // no memo set, expect empty memo
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("create entity without memo, expect empty memo")
                        .expected(getExpectedUpdatedEntity())
                        .input(new Entity())
                        .recordItem(getRecordItem(body, innerBody))
                        .build()
        );

        // memo set to empty string, expect empty memo
        Message updatedInnerBody = innerBody.toBuilder().setField(memoField, "").build();
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("create entity with empty memo, expect empty memo")
                        .expected(getExpectedUpdatedEntity())
                        .input(new Entity())
                        .recordItem(getRecordItem(body, updatedInnerBody))
                        .build()
        );

        // memo set to non-empty string, expect memo set
        Entity expected = getExpectedUpdatedEntity();
        expected.setMemo(DEFAULT_MEMO);
        updatedInnerBody = innerBody.toBuilder().setField(memoField, DEFAULT_MEMO).build();
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("create entity with non-empty memo, expect memo set")
                        .expected(expected)
                        .input(new Entity())
                        .recordItem(getRecordItem(body, updatedInnerBody))
                        .build()
        );

        return testSpecs;
    }

    private List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForUpdateTransaction(FieldDescriptor memoField,
                                                                                    FieldDescriptor memoWrapperField) {
        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        List<UpdateEntityTestSpec> testSpecs = new LinkedList<>();

        // memo not set, expect memo in entity unchanged
        Entity expected = getExpectedUpdatedEntity();
        expected.setMemo(DEFAULT_MEMO);
        Entity input = new Entity();
        input.setMemo(DEFAULT_MEMO);
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("update entity without memo, expect memo unchanged")
                        .expected(expected)
                        .input(input)
                        .recordItem(getRecordItem(body, innerBody))
                        .build()
        );

        if (memoWrapperField != null) {
            // memo is of string type
            // non-empty string, expect memo set to non-empty string
            expected = getExpectedUpdatedEntity();
            expected.setMemo(UPDATED_MEMO);
            input = new Entity();
            input.setMemo(DEFAULT_MEMO);
            Message updatedInnerBody = innerBody.toBuilder().setField(memoField, UPDATED_MEMO).build();
            testSpecs.add(
                    UpdateEntityTestSpec.builder()
                            .description("update entity with non-empty String, expect memo updated")
                            .expected(expected)
                            .input(input)
                            .recordItem(getRecordItem(body, updatedInnerBody))
                            .build()
            );
        }

        // memo is set through the StringValue field
        // there is always a StringValue field, either "memo" or "memoWrapper"
        FieldDescriptor field = memoWrapperField;
        if (field == null) {
            field = memoField;
        }

        // empty StringValue, expect memo in entity cleared
        input = new Entity();
        input.setMemo(DEFAULT_MEMO);
        Message updatedInnerBody = innerBody.toBuilder().setField(field, StringValue.of("")).build();
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("update entity with empty StringValue memo, expect memo cleared")
                        .expected(getExpectedUpdatedEntity())
                        .input(input)
                        .recordItem(getRecordItem(body, updatedInnerBody))
                        .build()
        );

        // non-empty StringValue, expect memo in entity updated
        expected = getExpectedUpdatedEntity();
        expected.setMemo(UPDATED_MEMO);
        input = new Entity();
        input.setMemo(DEFAULT_MEMO);
        updatedInnerBody = innerBody.toBuilder().setField(field, StringValue.of(UPDATED_MEMO)).build();
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("update entity with non-empty StringValue memo, expect memo updated")
                        .expected(expected)
                        .input(input)
                        .recordItem(getRecordItem(body, updatedInnerBody))
                        .build()
        );

        return testSpecs;
    }

    private Entity getExpectedUpdatedEntity() {
        Entity entity = new Entity();
        TransactionBody defaultBody = getDefaultTransactionBody().build();
        Message innerBody = getInnerBody(defaultBody);
        List<String> fieldNames = innerBody.getDescriptorForType().getFields().stream()
                .map(FieldDescriptor::getName)
                .collect(Collectors.toList());

        for (String fieldName : fieldNames) {
            switch (fieldName) {
                case "adminKey":
                    entity.setKey(DEFAULT_KEY.toByteArray());
                    break;
                case "autoRenewPeriod":
                    entity.setAutoRenewPeriod(DEFAULT_AUTO_RENEW_PERIOD.getSeconds());
                    break;
                case "expiry":
                case "expirationTime":
                    entity.setExpirationTimestamp(Utility.timestampInNanosMax(DEFAULT_EXPIRATION_TIME));
                    break;
                case "keys":
                    entity.setKey(DEFAULT_KEY_LIST.toByteArray());
                    break;
                case "submitKey":
                    entity.setSubmitKey(DEFAULT_SUBMIT_KEY.toByteArray());
                default:
                    break;
            }
        }

        // set created_timestamp and deleted based on transaction body type
        int dataCase = defaultBody.getDataCase().getNumber();
        switch (dataCase) {
            case 8: // contractCreate
            case 11: // cryptoCreate
            case 17: // fileCreate
            case 24: // consensusCreate
            case 29: // tokenCreate
            case 42: // scheduleCreate
                entity.setCreatedTimestamp(DEFAULT_CREATED_TIMESTAMP);
                entity.setDeleted(DEFAULT_DELETED);
                break;
            case 12: // cryptoDelete
            case 22: // contractDelete
            case 18: // fileDelete
            case 26: // consensusDelete
            case 35: // tokenDelete
            case 43: // scheduleDelete
                entity.setDeleted(true);
                break;
            default:
                break;
        }

        entity.setMemo("");

        return entity;
    }

    private TransactionBody getTransactionBodyForUpdateEntityWithoutMemo() {
        TransactionBody defaultBody = getDefaultTransactionBody().build();
        Message innerBody = getInnerBody(defaultBody);
        Message.Builder builder = innerBody.toBuilder().clear();

        for (FieldDescriptor field : innerBody.getDescriptorForType().getFields()) {
            switch (field.getName()) {
                case "adminKey":
                    builder.setField(field, DEFAULT_KEY);
                    break;
                case "autoRenewPeriod":
                    builder.setField(field, DEFAULT_AUTO_RENEW_PERIOD);
                    break;
                case "expiry":
                case "expirationTime":
                    builder.setField(field, DEFAULT_EXPIRATION_TIME);
                    break;
                case "keys":
                    builder.setField(field, DEFAULT_KEY_LIST);
                    break;
                case "submitKey":
                    builder.setField(field, DEFAULT_SUBMIT_KEY);
                default:
                    break;
            }
        }

        return getTransactionBody(defaultBody, builder.build());
    }

    private FieldDescriptor getInnerBodyFieldDescriptorByName(String name) {
        TransactionBody body = getDefaultTransactionBody().build();
        return getInnerBody(body).getDescriptorForType().findFieldByName(name);
    }

    private Message getInnerBody(TransactionBody body) {
        FieldDescriptor innerBodyField = body.getDescriptorForType()
                .findFieldByNumber(body.getDataCase().getNumber());
        return (Message) body.getField(innerBodyField);
    }

    private TransactionBody getTransactionBody(TransactionBody body, Message innerBody) {
        FieldDescriptor innerBodyField = body.getDescriptorForType()
                .findFieldByNumber(body.getDataCase().getNumber());
        return body.toBuilder().setField(innerBodyField, innerBody).build();
    }

    private RecordItem getRecordItem(TransactionBody body, Message innerBody) {
        return getRecordItem(getTransactionBody(body, innerBody));
    }

    protected RecordItem getRecordItem(TransactionBody body) {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(
                        SignedTransaction.newBuilder()
                                .setBodyBytes(body.toByteString())
                                .setSigMap(getDefaultSigMap())
                                .build()
                                .toByteString()
                )
                .build();
        return new RecordItem(transaction, TransactionRecord.newBuilder().build());
    }

    protected static Key getKey(String keyString) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(keyString)).build();
    }

    @Builder
    @Value
    static class UpdateEntityTestSpec {
        String description;
        Entity expected;
        Entity input;
        RecordItem recordItem;
    }
}
