package com.hedera.mirror.importer.parser.contractlog;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import javax.inject.Named;

import com.hedera.mirror.common.domain.transaction.RecordItem;

import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

import org.apache.tuweni.bytes.Bytes;

@Named
@RequiredArgsConstructor
public class SyntheticContractLogServiceImpl implements SyntheticContractLogService {

    private final EntityListener entityListener;

    @Override
    public void create(AbstractSyntheticContractLog log) {
        if (isContract(log.getRecordItem())) {
            return;
        }

        long consensusTimestamp = log.getRecordItem().getConsensusTimestamp();
        int logIndex = log.getRecordItem().incrementLogIndex();

        ContractLog contractLog = new ContractLog();

        contractLog.setBloom(Bytes.of(0).toArray());
        contractLog.setConsensusTimestamp(consensusTimestamp);
        contractLog.setContractId(log.getEntityId());
        contractLog.setData(log.getData());
        contractLog.setIndex(logIndex);
        contractLog.setRootContractId(log.getEntityId());
        contractLog.setPayerAccountId(log.getRecordItem().getPayerAccountId());
        contractLog.setTopic0(log.getTopic0());
        contractLog.setTopic1(log.getTopic1());
        contractLog.setTopic2(log.getTopic2());
        entityListener.onContractLog(contractLog);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult() || recordItem.getTransactionRecord().hasContractCreateResult();
    }
}
