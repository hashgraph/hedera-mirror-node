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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.StorageChange;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor
abstract class AbstractContractCallTransactionHandler implements TransactionHandler {

    protected final EntityIdService entityIdService;
    protected final EntityListener entityListener;
    protected final EntityProperties entityProperties;

    @SuppressWarnings("deprecation")
    protected final void onContractResult(RecordItem recordItem, ContractResult contractResult,
                                          ContractFunctionResult functionResult) {
        // set function result related properties where applicable
        if (functionResult != ContractFunctionResult.getDefaultInstance()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            List<Long> createdContractIds = new ArrayList<>();
            boolean persist = shouldPersistCreatedContractIDs(recordItem);

            for (ContractID createdContractId : functionResult.getCreatedContractIDsList()) {
                EntityId contractId = entityIdService.lookup(createdContractId);
                createdContractIds.add(contractId.getId());

                // The parent contract ID can also sometimes appear in the created contract IDs list, so exclude it
                if (persist && !EntityId.isEmpty(contractId) && !contractId.equals(
                        contractResult.getContractId())) {
                    doUpdateEntity(getContract(contractId, consensusTimestamp), recordItem);
                }
            }

            contractResult.setBloom(DomainUtils.toBytes(functionResult.getBloom()));
            contractResult.setCallResult(DomainUtils.toBytes(functionResult.getContractCallResult()));
            contractResult.setCreatedContractIds(createdContractIds);
            contractResult.setErrorMessage(functionResult.getErrorMessage());
            contractResult.setFunctionResult(functionResult.toByteArray());
            contractResult.setGasUsed(functionResult.getGasUsed());

            // contract call logs
            for (int index = 0; index < functionResult.getLogInfoCount(); ++index) {
                ContractLoginfo contractLoginfo = functionResult.getLogInfo(index);

                ContractLog contractLog = new ContractLog();
                contractLog.setBloom(DomainUtils.toBytes(contractLoginfo.getBloom()));
                contractLog.setConsensusTimestamp(consensusTimestamp);
                contractLog.setContractId(entityIdService.lookup(contractLoginfo.getContractID()));
                contractLog.setData(DomainUtils.toBytes(contractLoginfo.getData()));
                contractLog.setIndex(index);
                contractLog.setRootContractId(contractResult.getContractId());
                contractLog.setPayerAccountId(contractResult.getPayerAccountId());
                contractLog.setTopic0(Utility.getTopic(contractLoginfo, 0));
                contractLog.setTopic1(Utility.getTopic(contractLoginfo, 1));
                contractLog.setTopic2(Utility.getTopic(contractLoginfo, 2));
                contractLog.setTopic3(Utility.getTopic(contractLoginfo, 3));

                entityListener.onContractLog(contractLog);
            }

            // contract call state changes
            for (int stateIndex = 0; stateIndex < functionResult.getStateChangesCount(); ++stateIndex) {
                var contractStateChangeInfo = functionResult.getStateChanges(stateIndex);

                var contractId = entityIdService.lookup(contractStateChangeInfo.getContractID());
                for (int storageIndex = 0; storageIndex < contractStateChangeInfo
                        .getStorageChangesCount(); ++storageIndex) {
                    StorageChange storageChange = contractStateChangeInfo.getStorageChanges(storageIndex);

                    ContractStateChange contractStateChange = new ContractStateChange();
                    contractStateChange.setConsensusTimestamp(consensusTimestamp);
                    contractStateChange.setContractId(contractId);
                    contractStateChange.setPayerAccountId(contractResult.getPayerAccountId());
                    contractStateChange.setSlot(DomainUtils.toBytes(storageChange.getSlot()));
                    contractStateChange.setValueRead(DomainUtils.toBytes(storageChange.getValueRead()));

                    // If a value of zero is written the valueWritten will be present but the inner value will be
                    // absent. If a value was read and not written this value will not be present.
                    if (storageChange.hasValueWritten()) {
                        contractStateChange
                                .setValueWritten(DomainUtils.toBytes(storageChange.getValueWritten().getValue()));
                    }

                    entityListener.onContractStateChange(contractStateChange);
                }
            }
        }

        // Always persist a contract result whether partial or complete
        entityListener.onContractResult(contractResult);
    }

    protected abstract void doUpdateEntity(Contract contract, RecordItem recordItem);

    protected Contract getContract(EntityId contractId, long consensusTimestamp) {
        Contract contract = contractId.toEntity();
        contract.setCreatedTimestamp(consensusTimestamp);
        contract.setDeleted(false);
        contract.setTimestampLower(consensusTimestamp);
        return contract;
    }

    /**
     * Persist contract entities in createdContractIDs if it's prior to HAPI 0.23.0. After that the createdContractIDs
     * list is also externalized as contract create child records so we only need to persist the complete contract
     * entity from the child record.
     *
     * @param recordItem to check
     * @return Whether the createdContractIDs list should be persisted.
     */
    private boolean shouldPersistCreatedContractIDs(RecordItem recordItem) {
        return recordItem.isSuccessful() && entityProperties.getPersist().isContracts() &&
                recordItem.getHapiVersion().isLessThan(RecordFile.HAPI_VERSION_0_23_0);
    }
}
