/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.parser.contractlog;

import com.hedera.mirror.common.domain.contract.ContractLog;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;

@Named
@RequiredArgsConstructor
public class SyntheticContractLogServiceImpl implements SyntheticContractLogService {
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final byte[] empty = Bytes.of(0).toArray();

    @Override
    public void create(SyntheticContractLog log) {
        if (isContract(log.getRecordItem()) || !entityProperties.getPersist().isSyntheticContractLogs()) {
            return;
        }

        long consensusTimestamp = log.getRecordItem().getConsensusTimestamp();
        int logIndex = log.getRecordItem().getAndIncrementLogIndex();

        ContractLog contractLog = new ContractLog();

        contractLog.setBloom(empty);
        contractLog.setConsensusTimestamp(consensusTimestamp);
        contractLog.setContractId(log.getEntityId());
        contractLog.setData(log.getData() != null ? log.getData() : empty);
        contractLog.setIndex(logIndex);
        contractLog.setRootContractId(log.getEntityId());
        contractLog.setPayerAccountId(log.getRecordItem().getPayerAccountId());
        contractLog.setTopic0(log.getTopic0());
        contractLog.setTopic1(log.getTopic1());
        contractLog.setTopic2(log.getTopic2());
        contractLog.setTopic3(log.getTopic3());
        contractLog.setTransactionIndex(log.getRecordItem().getTransactionIndex());
        contractLog.setTransactionHash(log.getRecordItem().getTransactionHash());
        entityListener.onContractLog(contractLog);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }
}
