package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.fromBytes;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

class ContractCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EntityProperties entityProperties = new EntityProperties();

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(contractId)).thenReturn(EntityId.of(DEFAULT_ENTITY_NUM, CONTRACT));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractCreateTransactionHandler(entityIdService, entityListener, entityProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum).setContractID(contractId);
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForCreateTransaction(
            Descriptors.FieldDescriptor memoField) {
        List<UpdateEntityTestSpec> testSpecs = super.getUpdateEntityTestSpecsForCreateTransaction(memoField);

        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        body = getTransactionBody(body, innerBody);
        byte[] evmAddress = TestUtils.generateRandomByteArray(20);
        var contractCreateResult = ContractFunctionResult.newBuilder()
                .setEvmAddress(BytesValue.of(ByteString.copyFrom(evmAddress)));
        var recordBuilder = getDefaultTransactionRecord().setContractCreateResult(contractCreateResult);

        AbstractEntity expected = getExpectedUpdatedEntity();
        ((Contract) expected).setEvmAddress(evmAddress);
        expected.setMemo("");
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("create contract entity with evm address in record")
                        .expected(expected)
                        .recordItem(getRecordItem(body, recordBuilder.build()))
                        .build()
        );

        return testSpecs;
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return super.getDefaultTransactionRecord()
                .setContractCreateResult(ContractFunctionResult.newBuilder().addCreatedContractIDs(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return CONTRACT;
    }

    @CsvSource({
            "-1,-1,-1,1000,1000",
            "1,1,1,1000,1000",
            "0,0,9223372036854775807,1000,1000",
            "0,0,-1,0,2",
    })
    @ParameterizedTest
    void create2ContractIdWorkaround(long shard, long realm, long num, long resolvedNum, long expectedNum) {
        var invalidContractId = ContractID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setContractNum(num)
                .build();
        var evmAddress = ContractID.newBuilder().setEvmAddress(fromBytes(toEvmAddress(invalidContractId))).build();
        var expectedId = EntityId.of(expectedNum, CONTRACT);
        var resolvedId = EntityId.of(resolvedNum, CONTRACT);

        var transaction = domainBuilder.transaction().customize(t -> t.entityId(expectedId)).get();
        var recordItem = recordItemBuilder.contractCreate()
                .record(r -> {
                    r.getContractCreateResultBuilder().getLogInfoBuilder(0).setContractID(invalidContractId);
                    r.getContractCreateResultBuilder().getStateChangesBuilder(0).setContractID(invalidContractId);
                    r.getContractCreateResultBuilder().removeLogInfo(1);
                })
                .build();

        if (shard == 0 && realm == 0) {
            when(entityIdService.lookup(invalidContractId)).thenThrow(new RuntimeException(new InvalidEntityException("")));
        }
        when(entityIdService.lookup(evmAddress)).thenReturn(resolvedId);
        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onContractLog(assertArg(l -> assertThat(l.getContractId()).isEqualTo(expectedId)));
        verify(entityListener, times(2)).onContractStateChange(assertArg(s ->
                assertThat(s.getContractId()).isEqualTo(expectedId.getId())));
        verify(entityListener).onContractResult(isA(ContractResult.class));
    }
}
