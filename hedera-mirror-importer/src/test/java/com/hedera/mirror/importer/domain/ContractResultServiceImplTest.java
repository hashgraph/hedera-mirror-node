package com.hedera.mirror.importer.domain;

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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.StorageChange;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;

class ContractResultServiceImplTest extends IntegrationTest {
    protected static final ContractID CONTRACT_ID = ContractID.newBuilder().setContractNum(901).build();
    protected static final ContractID CREATED_CONTRACT_ID = ContractID.newBuilder().setContractNum(902).build();
    protected static final String KEY = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    protected static final AccountID PROXY = AccountID.newBuilder().setAccountNum(1003).build();
    protected static final EntityId PAYER_ENTITY_ID = EntityId.of(1000, ACCOUNT);

    @Resource
    private ContractResultService contractResultService;

    @Resource
    private DomainBuilder domainBuilder;

    @Mock
    private RecordItem recordItem;

    @Mock
    private TransactionRecord transactionRecord;

    @BeforeEach
    void setup() {
        doReturn(false).when(transactionRecord).hasContractCallResult();
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x.setContractID(CONTRACT_ID));
        doReturn(contractFunctionResult).when(transactionRecord).getContractCreateResult();
        doReturn(contractFunctionResult).when(transactionRecord).getContractCallResult();
        doReturn(transactionRecord).when(recordItem).getRecord();
        doReturn(100L).when(recordItem).getConsensusTimestamp();
        doReturn(PAYER_ENTITY_ID).when(recordItem).getPayerAccountId();
    }

    @Test
    void getContractResultOnCall() {
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x.setContractID(CONTRACT_ID));
        doReturn(contractFunctionResult).when(transactionRecord).getContractCallResult();
        doReturn(ContractFunctionResult.getDefaultInstance()).when(transactionRecord).getContractCreateResult();

        var createdIds = contractFunctionResult.getCreatedContractIDsList().stream().map(x -> x.getContractNum())
                .collect(Collectors.toList());
        assertThat(contractResultService.getContractResult(recordItem))
                .returns(100L, ContractResult::getConsensusTimestamp)
                .returns(PAYER_ENTITY_ID, ContractResult::getPayerAccountId)
                .returns(contractFunctionResult.getBloom().toByteArray(), ContractResult::getBloom)
                .returns(contractFunctionResult.getContractCallResult().toByteArray(), ContractResult::getCallResult)
                .returns(createdIds, ContractResult::getCreatedContractIds)
                .returns(contractFunctionResult.getErrorMessage(), ContractResult::getErrorMessage)
                .returns(contractFunctionResult.getGasUsed(), ContractResult::getGasUsed);
    }

    @Test
    void getContractResultOnCreate() {
        doReturn(true).when(transactionRecord).hasContractCreateResult();
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x.setContractID(CONTRACT_ID));
        doReturn(ContractFunctionResult.getDefaultInstance()).when(transactionRecord).getContractCallResult();
        doReturn(contractFunctionResult).when(transactionRecord).getContractCreateResult();

        var createdIds = contractFunctionResult.getCreatedContractIDsList().stream().map(x -> x.getContractNum())
                .collect(Collectors.toList());
        assertThat(contractResultService.getContractResult(recordItem))
                .returns(100L, ContractResult::getConsensusTimestamp)
                .returns(PAYER_ENTITY_ID, ContractResult::getPayerAccountId)
                .returns(contractFunctionResult.getBloom().toByteArray(), ContractResult::getBloom)
                .returns(contractFunctionResult.getContractCallResult().toByteArray(), ContractResult::getCallResult)
                .returns(createdIds, ContractResult::getCreatedContractIds)
                .returns(contractFunctionResult.getErrorMessage(), ContractResult::getErrorMessage)
                .returns(contractFunctionResult.getGasUsed(), ContractResult::getGasUsed);
    }

    @Test
    void getContractResultDefaultFunctionResult() {
        doReturn(ContractFunctionResult.getDefaultInstance()).when(transactionRecord).getContractCallResult();

        assertThat(contractResultService.getContractResult(recordItem))
                .returns(100L, ContractResult::getConsensusTimestamp)
                .returns(PAYER_ENTITY_ID, ContractResult::getPayerAccountId)
                .returns(null, ContractResult::getBloom)
                .returns(null, ContractResult::getCallResult)
                .returns(Collections.emptyList(), ContractResult::getCreatedContractIds)
                .returns(null, ContractResult::getErrorMessage)
                .returns(null, ContractResult::getGasUsed);
    }

    @Test
    void getContractLogs() {
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x.setContractID(CONTRACT_ID));
        ContractResult contractResult = domainBuilder.contractResult()
                .customize(x -> x.contractId(EntityId.of(CONTRACT_ID))).get();

        var listAssert = assertThat(contractResultService.getContractLogs(contractFunctionResult, contractResult))
                .hasSize(contractFunctionResult.getLogInfoCount());

        var blooms = contractFunctionResult.getLogInfoList().stream().map(x -> DomainUtils.toBytes(x.getBloom()))
                .collect(Collectors.toList());
        var data = contractFunctionResult.getLogInfoList().stream().map(x -> DomainUtils.toBytes(x.getData()))
                .collect(Collectors.toList());
        listAssert.extracting(ContractLog::getPayerAccountId).containsOnly(contractResult.getPayerAccountId());
        listAssert.extracting(ContractLog::getContractId).containsExactlyInAnyOrder(
                contractResult.getContractId(),
                EntityId.of(CREATED_CONTRACT_ID));
        listAssert.extracting(ContractLog::getRootContractId).containsOnly(contractResult.getContractId());
        listAssert.extracting(ContractLog::getConsensusTimestamp).containsOnly(contractResult.getConsensusTimestamp());
        listAssert.extracting(ContractLog::getIndex).containsExactlyInAnyOrder(0, 1);
        listAssert.extracting(ContractLog::getBloom).containsAll(blooms);
        listAssert.extracting(ContractLog::getData).containsAll(data);
    }

    @Test
    void getContractLogsZeroLogInfoCount() {
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x
                .setContractID(CONTRACT_ID)
                .clearLogInfo());
        ContractResult contractResult = domainBuilder.contractResult()
                .customize(x -> x.contractId(EntityId.of(CONTRACT_ID))).get();

        assertThat(contractResultService.getContractLogs(contractFunctionResult, contractResult)).isEmpty();
    }

    @Test
    void getContractLogsNullFunctionResult() {
        ContractResult contractResult = domainBuilder.contractResult()
                .customize(x -> x.contractId(EntityId.of(CONTRACT_ID))).get();

        assertThat(contractResultService.getContractLogs(null, contractResult)).isEmpty();
    }

    @Test
    void getContractLogsNullContractResult() {
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x
                .setContractID(CONTRACT_ID)
                .clearLogInfo());

        assertThat(contractResultService.getContractLogs(contractFunctionResult, null)).isEmpty();
    }

    @Test
    void getContractStateChanges() {
        ByteString[] slots = new ByteString[] {
                ByteString.copyFromUtf8("0x000000000000000000"),
                ByteString.copyFromUtf8("0x000000000000000001"),
                ByteString.copyFromUtf8("0x000000000000000002")
        };

        ByteString[] valuesRead = new ByteString[] {
                ByteString.copyFromUtf8("0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0"),
                ByteString.copyFromUtf8("0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df1"),
                ByteString.copyFromUtf8("0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df2")
        };

        ByteString[] valuesWritten = new ByteString[] {
                ByteString.copyFromUtf8("0x000000000000000000000000000000000000000000c2a8c408d0e29d623347c5"),
                ByteString.copyFromUtf8("0")
        };

        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x
                .setContractID(CONTRACT_ID)
                .clearStateChanges()
                .addStateChanges(com.hederahashgraph.api.proto.java.ContractStateChange.newBuilder()
                        .setContractID(CONTRACT_ID)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(slots[0])
                                .setValueRead(valuesRead[0])
                                .build())
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(slots[1])
                                .setValueRead(valuesRead[1])
                                .setValueWritten(BytesValue.of(valuesWritten[0]))
                                .build())
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(slots[2])
                                .setValueRead(valuesRead[2])
                                .setValueWritten(BytesValue.of(valuesWritten[1]))
                                .build())));

        ContractResult contractResult = domainBuilder.contractResult()
                .customize(x -> x.contractId(EntityId.of(CONTRACT_ID))).get();

        var listAssert = assertThat(contractResultService
                .getContractStateChanges(contractFunctionResult, contractResult))
                .hasSize(contractFunctionResult.getStateChanges(0).getStorageChangesCount());

        listAssert.extracting(ContractStateChange::getPayerAccountId).containsOnly(contractResult.getPayerAccountId());
        listAssert.extracting(ContractStateChange::getContractId).containsOnly(CONTRACT_ID.getContractNum());
        listAssert.extracting(ContractStateChange::getConsensusTimestamp)
                .containsOnly(contractResult.getConsensusTimestamp());
        listAssert.extracting(ContractStateChange::getSlot).containsExactlyInAnyOrder(
                DomainUtils.toBytes(slots[0]),
                DomainUtils.toBytes(slots[1]),
                DomainUtils.toBytes(slots[2]));
        listAssert.extracting(ContractStateChange::getValueRead).containsExactlyInAnyOrder(
                DomainUtils.toBytes(valuesRead[0]),
                DomainUtils.toBytes(valuesRead[1]),
                DomainUtils.toBytes(valuesRead[2]));
        listAssert.extracting(ContractStateChange::getValueWritten).containsExactlyInAnyOrder(
                null,
                DomainUtils.toBytes(valuesWritten[0]),
                DomainUtils.toBytes(valuesWritten[1]));
    }

    @Test
    void getContractStateChangesEmptyStateChanges() {
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x
                .setContractID(CONTRACT_ID)
                .clearStateChanges());

        ContractResult contractResult = domainBuilder.contractResult()
                .customize(x -> x.contractId(EntityId.of(CONTRACT_ID))).get();

        assertThat(contractResultService
                .getContractStateChanges(contractFunctionResult, contractResult))
                .isEmpty();
    }

    @Test
    void getContractStateChangesNullFunctionResult() {
        ContractResult contractResult = domainBuilder.contractResult()
                .customize(x -> x.contractId(EntityId.of(CONTRACT_ID))).get();

        assertThat(contractResultService
                .getContractStateChanges(null, contractResult))
                .isEmpty();
    }

    @Test
    void getContractStateChangesNullContractResult() {
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x
                .setContractID(CONTRACT_ID)
                .clearStateChanges());

        assertThat(contractResultService
                .getContractStateChanges(contractFunctionResult, null))
                .isEmpty();
    }

    private ContractFunctionResult getContractFunctionResult(Consumer<ContractFunctionResult.Builder> customBuilder) {
        var contractFunctionResultBuilder = ContractFunctionResult.newBuilder()
                .setBloom(ByteString.copyFromUtf8("bloom"))
                .setContractCallResult(ByteString.copyFromUtf8("call result"))
                .setContractID(CONTRACT_ID)
                .addCreatedContractIDs(CONTRACT_ID)
                .addCreatedContractIDs(CREATED_CONTRACT_ID)
                .setErrorMessage("call error message")
                .setEvmAddress(BytesValue.of(DomainUtils.fromBytes(domainBuilder.create2EvmAddress())))
                .setGasUsed(30)
                .addLogInfo(ContractLoginfo.newBuilder()
                        .setBloom(ByteString.copyFromUtf8("bloom"))
                        .setContractID(CONTRACT_ID)
                        .setData(ByteString.copyFromUtf8("data"))
                        .addTopic(ByteString.copyFromUtf8("Topic0"))
                        .addTopic(ByteString.copyFromUtf8("Topic1"))
                        .addTopic(ByteString.copyFromUtf8("Topic2"))
                        .addTopic(ByteString.copyFromUtf8("Topic3")).build())
                .addLogInfo(ContractLoginfo.newBuilder()
                        .setBloom(ByteString.copyFromUtf8("bloom"))
                        .setContractID(CREATED_CONTRACT_ID)
                        .setData(ByteString.copyFromUtf8("data"))
                        .addTopic(ByteString.copyFromUtf8("Topic0"))
                        .addTopic(ByteString.copyFromUtf8("Topic1"))
                        .addTopic(ByteString.copyFromUtf8("Topic2"))
                        .addTopic(ByteString.copyFromUtf8("Topic3")).build())
                // 3 state changes, no value written, valid value written and zero value written
                .addStateChanges(com.hederahashgraph.api.proto.java.ContractStateChange.newBuilder()
                        .setContractID(CONTRACT_ID)
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(ByteString
                                        .copyFromUtf8("0x000000000000000000"))
                                .setValueRead(ByteString
                                        .copyFromUtf8(
                                                "0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0"))
                                .build())
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(ByteString
                                        .copyFromUtf8("0x000000000000000001"))
                                .setValueRead(ByteString
                                        .copyFromUtf8(
                                                "0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0"))
                                .setValueWritten(BytesValue.of(ByteString
                                        .copyFromUtf8(
                                                "0x000000000000000000000000000000000000000000c2a8c408d0e29d623347c5")))
                                .build())
                        .addStorageChanges(StorageChange.newBuilder()
                                .setSlot(ByteString
                                        .copyFromUtf8("0x00000000000000002"))
                                .setValueRead(ByteString
                                        .copyFromUtf8(
                                                "0xaf846d22986843e3d25981b94ce181adc556b334ccfdd8225762d7f709841df0"))
                                .setValueWritten(BytesValue.of(ByteString.copyFromUtf8("0")))
                                .build())
                        .build());

        if (customBuilder != null) {
            customBuilder.accept(contractFunctionResultBuilder);
        }

        return contractFunctionResultBuilder.build();
    }
}
