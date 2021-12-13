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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor
abstract class AbstractContractCallTransactionHandler implements TransactionHandler {

    protected final EntityListener entityListener;
    protected final EntityProperties entityProperties;

    protected final void onContractResult(RecordItem recordItem, ContractResult contractResult,
                                          ContractFunctionResult functionResult) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        List<Long> createdContractIds = new ArrayList<>();
        boolean persist = recordItem.isSuccessful() && entityProperties.getPersist().isContracts();

        for (ContractID createdContractId : functionResult.getCreatedContractIDsList()) {
            EntityId contractId = EntityId.of(createdContractId);
            createdContractIds.add(contractId.getId());

            // The parent contract ID can also sometimes appear in the created contract IDs list, so exclude it
            if (persist && !EntityId.isEmpty(contractId) && !contractId.equals(contractResult.getContractId())) {
                Contract contract = contractId.toEntity();
                contract.setCreatedTimestamp(consensusTimestamp);
                contract.setDeleted(false);
                contract.setModifiedTimestamp(consensusTimestamp);
                doUpdateEntity(contract, recordItem);
            }
        }

        contractResult.setBloom(DomainUtils.toBytes(functionResult.getBloom()));
        contractResult.setCallResult(DomainUtils.toBytes(functionResult.getContractCallResult()));
        contractResult.setCreatedContractIds(createdContractIds);
        contractResult.setErrorMessage(functionResult.getErrorMessage());
        contractResult.setFunctionResult(functionResult.toByteArray());
        contractResult.setGasUsed(functionResult.getGasUsed());
        entityListener.onContractResult(contractResult);

        for (int index = 0; index < functionResult.getLogInfoCount(); ++index) {
            ContractLoginfo contractLoginfo = functionResult.getLogInfo(index);

            ContractLog contractLog = new ContractLog();
            contractLog.setBloom(DomainUtils.toBytes(contractLoginfo.getBloom()));
            contractLog.setConsensusTimestamp(consensusTimestamp);
            contractLog.setContractId(EntityId.of(contractLoginfo.getContractID()));
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
    }

    protected abstract void doUpdateEntity(Contract contract, RecordItem recordItem);
}
