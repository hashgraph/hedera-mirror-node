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
import java.util.ArrayList;
import java.util.List;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

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

    private List<Long> getCreatedContractIds(ContractFunctionResult functionResult) {
        List<Long> createdContractIds = new ArrayList<>();
        for (ContractID createdContractId : functionResult.getCreatedContractIDsList()) {
            EntityId contractId = entityIdService.lookup(createdContractId);
            createdContractIds.add(contractId.getId());
        }

        return createdContractIds;
    }
}
