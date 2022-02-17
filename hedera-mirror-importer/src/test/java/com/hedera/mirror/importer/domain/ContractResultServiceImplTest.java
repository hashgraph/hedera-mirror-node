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
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;

class ContractResultServiceImplTest extends IntegrationTest {
    protected static final ContractID CONTRACT_ID = ContractID.newBuilder().setContractNum(901).build();
    protected static final ContractID CREATED_CONTRACT_ID = ContractID.newBuilder().setContractNum(902).build();
    //    protected static final SignatureMap DEFAULT_SIG_MAP = getDefaultSigMap();
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
        doReturn(true).when(transactionRecord).hasContractCallResult();
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x.setContractID(CONTRACT_ID));
        doReturn(contractFunctionResult).when(transactionRecord).getContractCreateResult();
        doReturn(contractFunctionResult).when(transactionRecord).getContractCallResult();
        doReturn(true).when(transactionRecord).hasContractCallResult();
        doReturn(transactionRecord).when(recordItem).getRecord();
        doReturn(100L).when(recordItem).getConsensusTimestamp();
        doReturn(PAYER_ENTITY_ID).when(recordItem).getPayerAccountId();
    }

    @Test
    void getContractResult() {
        ContractFunctionResult contractFunctionResult = getContractFunctionResult(x -> x.setContractID(CONTRACT_ID));
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

    @Disabled
    @Test
    void getContractLogs() {
    }

    @Disabled
    @Test
    void getContractStateChanges() {
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
