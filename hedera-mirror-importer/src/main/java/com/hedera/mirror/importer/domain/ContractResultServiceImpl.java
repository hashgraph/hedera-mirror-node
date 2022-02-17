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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.contract.ContractStateChange;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor
public class ContractResultServiceImpl implements ContractResultService {

    protected final EntityProperties entityProperties;
    private final EntityIdService entityIdService;

    @Override
    public ContractResult getContractResult(RecordItem recordItem) {
        ContractResult contractResult = new ContractResult();
        contractResult.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        contractResult.setPayerAccountId(recordItem.getPayerAccountId());

        var functionResult = recordItem.getRecord().hasContractCreateResult() ?
                recordItem.getRecord().getContractCreateResult() : recordItem.getRecord().getContractCallResult();
        if (functionResult != ContractFunctionResult.getDefaultInstance() && functionResult.hasContractID()) {
            contractResult.setBloom(DomainUtils.toBytes(functionResult.getBloom()));
            contractResult.setCallResult(DomainUtils.toBytes(functionResult.getContractCallResult()));
            contractResult.setContractId(entityIdService.lookup(functionResult.getContractID()));
            contractResult.setCreatedContractIds(getCreatedContractIds(functionResult));
            contractResult.setErrorMessage(functionResult.getErrorMessage());
            contractResult.setFunctionResult(functionResult.toByteArray());
            contractResult.setGasUsed(functionResult.getGasUsed());

            // amount, gasLimit and functionParameters are missing from record proto and will be populated once added
        }

        return contractResult;
    }

    @Override
    public List<ContractLog> getContractLogs(ContractFunctionResult functionResult, ContractResult contractResult) {

        List<ContractLog> contractLogs = new ArrayList<>();
        for (int index = 0; index < functionResult.getLogInfoCount(); ++index) {
            ContractLoginfo contractLoginfo = functionResult.getLogInfo(index);

            ContractLog contractLog = new ContractLog();
            contractLog.setBloom(DomainUtils.toBytes(contractLoginfo.getBloom()));
            contractLog.setConsensusTimestamp(contractResult.getConsensusTimestamp());
            contractLog.setContractId(entityIdService.lookup(contractLoginfo.getContractID()));
            contractLog.setData(DomainUtils.toBytes(contractLoginfo.getData()));
            contractLog.setIndex(index);
            contractLog.setRootContractId(contractResult.getContractId());
            contractLog.setPayerAccountId(contractResult.getPayerAccountId());
            contractLog.setTopic0(Utility.getTopic(contractLoginfo, 0));
            contractLog.setTopic1(Utility.getTopic(contractLoginfo, 1));
            contractLog.setTopic2(Utility.getTopic(contractLoginfo, 2));
            contractLog.setTopic3(Utility.getTopic(contractLoginfo, 3));
            contractLogs.add(contractLog);
        }

        return contractLogs;
    }

    @Override
    public List<ContractStateChange> getContractStateChanges(ContractFunctionResult functionResult,
                                                             ContractResult contractResult) {

        List<ContractStateChange> contractStateChanges = new ArrayList<>();
        for (int stateIndex = 0; stateIndex < functionResult.getStateChangesCount(); ++stateIndex) {
            var contractStateChangeInfo = functionResult.getStateChanges(stateIndex);

            var contractId = entityIdService.lookup(contractStateChangeInfo.getContractID());
            for (var storageChange : contractStateChangeInfo.getStorageChangesList()) {
                ContractStateChange contractStateChange = new ContractStateChange();
                contractStateChange.setConsensusTimestamp(contractResult.getConsensusTimestamp());
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

                contractStateChanges.add(contractStateChange);
            }
        }

        return contractStateChanges;
    }

    private List<Long> getCreatedContractIds(ContractFunctionResult functionResult) {
        List<Long> createdContractIds = new ArrayList<>();
        for (ContractID createdContractId : functionResult.getCreatedContractIDsList()) {
            EntityId contractId = entityIdService.lookup(createdContractId);
            createdContractIds.add(contractId.getId());
        }

        return createdContractIds;
    }
}
