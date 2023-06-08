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

package com.hedera.mirror.importer.parser.contractresult;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class SyntheticContractResultServiceImpl implements SyntheticContractResultService {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public void create(SyntheticContractResult result) {
        if (isContract(result.getRecordItem()) || !entityProperties.getPersist().isSyntheticContractResults()) {
            return;
        }

        long consensusTimestamp = result.getRecordItem().getConsensusTimestamp();
        long gasLimit = result.getRecordItem().getTransactionBody().getTransactionFee();

        ContractResult contractResult = new ContractResult();

        contractResult.setConsensusTimestamp(consensusTimestamp);
        contractResult.setContractId(result.getEntityId().getId());
        contractResult.setSenderId(result.getSenderId());
        contractResult.setFunctionParameters(result.getFunctionParameters());
        contractResult.setGasLimit(gasLimit);
        contractResult.setTransactionResult(0);
        contractResult.setPayerAccountId(result.getRecordItem().getPayerAccountId());
        contractResult.setTransactionHash(result.getRecordItem().getTransactionHash());
        contractResult.setTransactionIndex(result.getRecordItem().getTransactionIndex());

        entityListener.onContractResult(contractResult);
    }

    private boolean isContract(RecordItem recordItem) {
        return recordItem.getTransactionRecord().hasContractCallResult()
                || recordItem.getTransactionRecord().hasContractCreateResult();
    }
}
