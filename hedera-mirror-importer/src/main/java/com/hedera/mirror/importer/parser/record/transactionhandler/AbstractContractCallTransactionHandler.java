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
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.Contract;
import com.hedera.mirror.importer.domain.ContractLog;
import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor
abstract class AbstractContractCallTransactionHandler implements TransactionHandler {

    protected final EntityListener entityListener;

    protected final void onContractResult(RecordItem recordItem, Supplier<Contract> inheritedContract,
                                          ContractResult contractResult,
                                          ContractFunctionResult functionResult) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        List<Long> createdContractIds = new ArrayList<>();
        boolean isSuccessful = recordItem.isSuccessful();

        for (ContractID createdContractId : functionResult.getCreatedContractIDsList()) {
            EntityId contractId = EntityId.of(createdContractId);
            createdContractIds.add(contractId.getId());

            if (isSuccessful && !EntityId.isEmpty(contractId) && !contractId.equals(contractResult.getContractId())) {
                Contract contract = inheritedContract.get();
                contract.setCreatedTimestamp(consensusTimestamp);
                contract.setDeleted(false);
                contract.setId(contractId.getId());
                contract.setModifiedTimestamp(consensusTimestamp);
                contract.setNum(contractId.getEntityNum());
                contract.setParentId(contractResult.getContractId());
                contract.setRealm(contractId.getRealmNum());
                contract.setShard(contractId.getShardNum());
                contract.setType(contractId.getType());
                doUpdateEntity(contract, recordItem);
            }
        }

        TransactionBody body = recordItem.getTransactionBody();
        var payerAccount = EntityId.of(body.getTransactionID().getAccountID());

        contractResult.setBloom(Utility.toBytes(functionResult.getBloom()));
        contractResult.setCallResult(Utility.toBytes(functionResult.getContractCallResult()));
        contractResult.setCreatedContractIds(createdContractIds);
        contractResult.setErrorMessage(functionResult.getErrorMessage());
        contractResult.setFunctionResult(functionResult.toByteArray());
        contractResult.setGasUsed(functionResult.getGasUsed());
        entityListener.onContractResult(contractResult);

        for (int index = 0; index < functionResult.getLogInfoCount(); ++index) {
            ContractLoginfo contractLoginfo = functionResult.getLogInfo(index);
            var topics = contractLoginfo.getTopicList();

            ContractLog contractLog = new ContractLog();
            contractLog.setBloom(Utility.toBytes(contractLoginfo.getBloom()));
            contractLog.setConsensusTimestamp(consensusTimestamp);
            contractLog.setContractId(EntityId.of(contractLoginfo.getContractID()));
            contractLog.setData(Utility.toBytes(contractLoginfo.getData()));
            contractLog.setIndex(index);
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
