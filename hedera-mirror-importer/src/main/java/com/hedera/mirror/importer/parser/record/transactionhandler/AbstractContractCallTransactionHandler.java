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

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.importer.domain.ContractResultService;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@Log4j2
@RequiredArgsConstructor
abstract class AbstractContractCallTransactionHandler implements TransactionHandler {

    protected final ContractResultService contractResultService;
    protected final EntityIdService entityIdService;
    protected final EntityListener entityListener;
    protected final EntityProperties entityProperties;

    protected abstract void doUpdateEntity(Contract contract, RecordItem recordItem);

    protected Contract getContract(EntityId contractId, long consensusTimestamp) {
        Contract contract = contractId.toEntity();
        contract.setCreatedTimestamp(consensusTimestamp);
        contract.setDeleted(false);
        contract.setTimestampLower(consensusTimestamp);
        return contract;
    }

    protected ContractResult getBaseContractResult(Transaction transaction, RecordItem recordItem) {
        ContractResult contractResult = contractResultService.getContractResult(recordItem);
        contractResult.setContractId(transaction.getEntityId()); // overwrite for case of null entityId on failure

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        boolean persist = shouldPersistCreatedContractIDs(recordItem);

        for (Long contractEncodedId : contractResult.getCreatedContractIds()) {
            EntityId contractId = EntityId.of(contractEncodedId, EntityType.CONTRACT);
            // The parent contract ID can also sometimes appear in the created contract IDs list, so exclude it
            if (persist && !EntityId.isEmpty(contractId) && !contractId.equals(
                    contractResult.getContractId())) {
                doUpdateEntity(getContract(contractId, consensusTimestamp), recordItem);
            }
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
}
