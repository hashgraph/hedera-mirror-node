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

import static com.hedera.mirror.importer.domain.StreamFilename.FileType.DATA;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.IterableAssert;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.StreamType;
import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.repository.ContractActionRepository;
import com.hedera.mirror.importer.repository.ContractLogRepository;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.ContractStateChangeRepository;
import com.hedera.services.stream.proto.ContractActionType;
import com.hedera.services.stream.proto.ContractActions;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class ContractResultServiceImplIntegrationTest extends IntegrationTest {
    private static final ContractID CONTRACT_ID = ContractID.newBuilder().setContractNum(901).build();

    private final ContractActionRepository contractActionRepository;
    private final ContractLogRepository contractLogRepository;
    private final ContractResultRepository contractResultRepository;
    private final ContractResultService contractResultService;
    private final ContractStateChangeRepository contractStateChangeRepository;
    private final DomainBuilder domainBuilder;
    private final RecordItemBuilder recordItemBuilder;
    private final RecordStreamFileListener recordStreamFileListener;
    private final TransactionTemplate transactionTemplate;

    @Test
    void getContractResultOnCall() {
        RecordItem recordItem = recordItemBuilder.contractCall().build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCallResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void getContractResultOnCreate() {
        RecordItem recordItem = recordItemBuilder.contractCreate(CONTRACT_ID).build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCreateResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void getContractResultOnTokenMintFT() {
        RecordItem recordItem = recordItemBuilder.tokenMint(TokenType.FUNGIBLE_COMMON)
                .record(x -> x.setContractCallResult(recordItemBuilder.contractFunctionResult(CONTRACT_ID)))
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCallResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void getContractResultOnTokenMintNFT() {
        RecordItem recordItem = recordItemBuilder.tokenMint(TokenType.NON_FUNGIBLE_UNIQUE)
                .record(x -> x.setContractCallResult(recordItemBuilder.contractFunctionResult(CONTRACT_ID)))
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCallResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void getContractCallResultDefaultFunctionResult() {
        RecordItem recordItem = recordItemBuilder.contractCall()
                .record(x -> x.clearContractCallResult())
                .sidecarRecord(r -> {
                    r.get(0).clearSidecarRecords();
                    r.get(1).clearSidecarRecords();
                })
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCreateResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void getContractCreateResultDefaultFunctionResult() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .record(x -> x.clearContractCreateResult())
                .sidecarRecord(r -> {
                    r.get(0).clearSidecarRecords();
                    r.get(1).clearSidecarRecords();
                })
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCreateResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void contractResultZeroLogs() {
        RecordItem recordItem = recordItemBuilder.contractCall()
                .record(x -> x.setContractCallResult(recordItemBuilder.contractFunctionResult(CONTRACT_ID)
                        .clearLogInfo()))
                .sidecarRecord(r -> {
                    r.get(0).clearSidecarRecords();
                    r.get(1).clearSidecarRecords();
                })
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCallResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void contractResultZeroStateChanges() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .record(x -> x.setContractCreateResult(recordItemBuilder.contractFunctionResult(CONTRACT_ID)))
                .receipt(r -> r.setContractID(CONTRACT_ID))
                .sidecarRecord(r -> {
                    r.get(0).clearSidecarRecords();
                    r.get(1).clearSidecarRecords();
                })
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCreateResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void contractCallResultOnFailure() {
        RecordItem recordItem = recordItemBuilder.contractCall()
                .record(x -> x.clearContractCallResult())
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .sidecarRecord(r -> {
                    r.get(0).clearSidecarRecords();
                    r.get(1).clearSidecarRecords();
                })
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCreateResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void contractCreateResultOnFailure() {
        RecordItem recordItem = recordItemBuilder.contractCreate()
                .record(x -> x.clearContractCreateResult())
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .sidecarRecord(r -> {
                    r.get(0).clearSidecarRecords();
                    r.get(1).clearSidecarRecords();
                })
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCreateResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    @Test
    void contractActionsTest() {
        var transactionsSidecar = recordItemBuilder.transactionSidecarRecord(CONTRACT_ID).build();
        var contractRecipientContract = ContractActions.newBuilder()
                .addContractActions(transactionsSidecar.getActions().getContractActions(0));
        var accountRecipientAccount = ContractActions.newBuilder()
                .addContractActions(transactionsSidecar.getActions().getContractActions(1));
        var contractRecipientAddress = ContractActions.newBuilder()
                .addContractActions(transactionsSidecar.getActions().getContractActions(2));
        RecordItem recordItem = recordItemBuilder.contractCall()
                .sidecarRecord(r -> r.get(0).setActions(contractRecipientContract))
                .sidecarRecord(r -> r.get(1).setActions(accountRecipientAccount))
                .sidecarRecord(r -> r.get(2).setActions(contractRecipientAddress))
                .build();
        ContractFunctionResult contractFunctionResult = recordItem.getRecord().getContractCallResult();

        contractResultsTest(recordItem, contractFunctionResult);
    }

    private void contractResultsTest(RecordItem recordItem, ContractFunctionResult contractFunctionResult) {
        var transaction = domainBuilder.transaction().customize(t -> t
                .entityId(EntityId.of(recordItem.getRecord().getReceipt().getContractID()))
                .type(recordItem.getTransactionType())).get();

        var createdIds = contractFunctionResult.getCreatedContractIDsList().stream().map(x -> x.getContractNum())
                .collect(Collectors.toList());
        parseRecordItemAndCommit(recordItem, transaction);

        ObjectAssert<ContractResult> contractResultAssert = assertThat(contractResultRepository.findAll())
                .hasSize(1)
                .first();
        contractResultAssert
                .returns(recordItem.getConsensusTimestamp(), ContractResult::getConsensusTimestamp)
                .returns(recordItem.getPayerAccountId(), ContractResult::getPayerAccountId)
                .returns(parseContractResultBytes(contractFunctionResult.getBloom()), ContractResult::getBloom)
                .returns(parseContractResultBytes(contractFunctionResult.getContractCallResult()),
                        ContractResult::getCallResult)
                .returns(createdIds, ContractResult::getCreatedContractIds)
                .returns(parseContractResultStrings(contractFunctionResult.getErrorMessage()),
                        ContractResult::getErrorMessage)
                .returns(parseContractResultLongs(contractFunctionResult.getGasUsed()), ContractResult::getGasUsed);

        assertContractLogs(contractFunctionResult, recordItem);
        assertContractStateChanges(recordItem);
        assertContractActions(recordItem);
    }

    private byte[] parseContractResultBytes(ByteString byteString) {
        return byteString == ByteString.EMPTY ? null : DomainUtils.toBytes(byteString);
    }

    private String parseContractResultStrings(String message) {
        return StringUtils.isEmpty(message) ? null : message;
    }

    private Long parseContractResultLongs(long num) {
        return num == 0 ? null : num;
    }

    private void assertContractActions(RecordItem recordItem) {
        var contractActions = recordItem.getSidecarLists().getLeft();
        if (contractActions.size() > 0) {
            IterableAssert<com.hedera.mirror.common.domain.contract.ContractAction> listAssert = assertThat(contractActionRepository.findAll())
                    .hasSize(contractActions.size());

            var callers = new ArrayList<EntityId>();
            var callTypes = new ArrayList<EntityType>();
            var contractActionTypes = new ArrayList<ContractActionType>();
            var recipientAccounts = new ArrayList<EntityId>();
            var recipientContracts = new ArrayList<EntityId>();
            var recipientAddresses = new ArrayList<byte[]>();
            var inputs = new ArrayList<byte[]>();
            var outputs = new ArrayList<byte[]>();

            contractActions.forEach(a -> {
                switch (a.getCallerCase()) {
                    case CALLING_CONTRACT -> {
                        callers.add(EntityId.of(a.getCallingContract()));
                        callTypes.add(EntityType.CONTRACT);
                    }
                    default -> {
                        callers.add(EntityId.of(a.getCallingAccount()));
                        callTypes.add(EntityType.ACCOUNT);
                    }

                }

                switch (a.getRecipientCase()) {
                    case RECIPIENT_CONTRACT -> recipientContracts.add(EntityId.of(a.getRecipientContract()));
                    case RECIPIENT_ACCOUNT -> recipientAccounts.add(EntityId.of(a.getRecipientAccount()));
                    default -> recipientAddresses.add(DomainUtils.toBytes(a.getInvalidSolidityAddress()));
                }

                contractActionTypes.add(a.getCallType());

                inputs.add(a.getInput().toByteArray());
                outputs.add(a.getOutput().toByteArray());
            });

            listAssert.extracting(ContractAction::getCallDepth).containsOnly(contractActions.get(0).getCallDepth());
            listAssert.extracting(ContractAction::getCaller).containsAll(callers);
            listAssert.extracting(ContractAction::getConsensusTimestamp).containsOnly(recordItem.getConsensusTimestamp());
            listAssert.extracting(ContractAction::getRecipientAccount).containsAll(recipientAccounts);
            listAssert.extracting(ContractAction::getRecipientContract).containsAll(recipientContracts);
            listAssert.extracting(ContractAction::getRecipientAddress).containsAll(recipientAddresses);
            listAssert.extracting(ContractAction::getGas).containsOnly(contractActions.get(0).getGas());
            listAssert.extracting(ContractAction::getGasUsed).containsOnly(contractActions.get(0).getGasUsed());
            listAssert.extracting(ContractAction::getCallerType).containsAll(callTypes);
            listAssert.extracting(ContractAction::getCallType).containsOnly(contractActionTypes.get(0).getNumber());
            listAssert.extracting(ContractAction::getInput).containsAll(inputs);
            listAssert.extracting(ContractAction::getResultData).containsAll(outputs);
            listAssert.extracting(ContractAction::getResultDataType)
                    .containsOnly(contractActions.get(0).getResultDataCase().getNumber());
            listAssert.extracting(ContractAction::getValue).containsOnly(contractActions.get(0).getValue());
        }
    }

    private void assertContractLogs(ContractFunctionResult contractFunctionResult, RecordItem recordItem) {
        if (contractFunctionResult.getLogInfoCount() > 0) {
            var listAssert = assertThat(contractLogRepository.findAll())
                    .hasSize(contractFunctionResult.getLogInfoCount());

            var blooms = new ArrayList<byte[]>();
            var contractIds = new ArrayList<EntityId>();
            var data = new ArrayList<byte[]>();
            contractFunctionResult.getLogInfoList().forEach(x -> {
                blooms.add(DomainUtils.toBytes(x.getBloom()));
                contractIds.add(EntityId.of(x.getContractID()));
                data.add(DomainUtils.toBytes(x.getData()));
            });

            listAssert.extracting(ContractLog::getPayerAccountId).containsOnly(recordItem.getPayerAccountId());
            listAssert.extracting(ContractLog::getContractId).containsAll(contractIds);
            listAssert.extracting(ContractLog::getRootContractId)
                    .containsOnly(EntityId.of(contractFunctionResult.getContractID()));
            listAssert.extracting(ContractLog::getConsensusTimestamp).containsOnly(recordItem.getConsensusTimestamp());
            listAssert.extracting(ContractLog::getIndex).containsExactlyInAnyOrder(0, 1);
            listAssert.extracting(ContractLog::getBloom).containsAll(blooms);
            listAssert.extracting(ContractLog::getData).containsAll(data);
        }
    }

    private void assertContractStateChanges(RecordItem recordItem) {
        var stateChanges = recordItem.getSidecarLists().getRight();
        if (stateChanges.size() > 0) {
            var listAssert = assertThat(contractStateChangeRepository.findAll())
                    .hasSize(stateChanges.get(0).getStorageChangesCount());

            var contractIds = new ArrayList<Long>();
            var slots = new ArrayList<byte[]>();
            var valuesRead = new ArrayList<byte[]>();
            var valuesWritten = new ArrayList<byte[]>();
            stateChanges.forEach(x -> {
                contractIds.add(EntityId.of(x.getContractId()).getId());
                x.getStorageChangesList().forEach(y -> {
                    slots.add(DomainUtils.toBytes(y.getSlot()));
                    valuesRead.add(DomainUtils.toBytes(y.getValueRead()));
                    valuesWritten.add(DomainUtils.toBytes(y.getValueWritten().getValue()));
                });
            });

            listAssert.extracting(ContractStateChange::getPayerAccountId).containsOnly(recordItem.getPayerAccountId());
            listAssert.extracting(ContractStateChange::getContractId).containsAll(contractIds);
            listAssert.extracting(ContractStateChange::getConsensusTimestamp)
                    .containsOnly(recordItem.getConsensusTimestamp());
            listAssert.extracting(ContractStateChange::getSlot).containsAll(slots);
            listAssert.extracting(ContractStateChange::getValueRead).containsAll(valuesRead);
            listAssert.extracting(ContractStateChange::getValueWritten).containsAll(valuesWritten);
        }
    }

    protected void parseRecordItemAndCommit(RecordItem recordItem, Transaction transaction) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
            String filename = StreamFilename.getFilename(StreamType.RECORD, DATA, instant);
            long consensusStart = recordItem.getConsensusTimestamp();
            RecordFile recordFile = domainBuilder.recordFile().customize(x -> x
                            .consensusStart(consensusStart)
                            .consensusEnd(consensusStart + 1)
                            .name(filename))
                    .get();

            recordStreamFileListener.onStart();
            contractResultService.process(recordItem, transaction);
            // commit, close connection
            recordStreamFileListener.onEnd(recordFile);
        });
    }
}
