package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
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

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

class CryptoCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoCreateTransactionHandler();
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return super.getDefaultTransactionRecord()
                .setReceipt(TransactionReceipt.newBuilder()
                        .setAccountID(AccountID.newBuilder().setAccountNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityTypeEnum getExpectedEntityIdType() {
        return EntityTypeEnum.ACCOUNT;
    }

    @Override
    protected Entity getExpectedUpdatedEntity() {
        Entity entity = super.getExpectedUpdatedEntity();
        entity.setMaxAutomaticTokenAssociations(0L);
        return entity;
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForCreateTransaction(FieldDescriptor memoField) {
        List<UpdateEntityTestSpec> testSpecs = super.getUpdateEntityTestSpecsForCreateTransaction(memoField);

        // add a test spec with max_automatic_token_associations 0xffffffff, to verify the handler properly convert
        // the protobuf uint32 value presented as a Java int
        int value = 0xffffffff;
        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        FieldDescriptor field = getInnerBodyFieldDescriptorByName("max_automatic_token_associations");
        innerBody = innerBody.toBuilder().setField(field, value).build();
        body = getTransactionBody(body, innerBody);

        Entity expected = getExpectedUpdatedEntity();
        expected.setMaxAutomaticTokenAssociations(Integer.toUnsignedLong(value));
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("create entity with 0xffffffff max_automatic_token_associations")
                        .expected(expected)
                        .input(new Entity())
                        .recordItem(getRecordItem(body, getDefaultTransactionRecord().build()))
                        .build()
        );

        return testSpecs;
    }
}
