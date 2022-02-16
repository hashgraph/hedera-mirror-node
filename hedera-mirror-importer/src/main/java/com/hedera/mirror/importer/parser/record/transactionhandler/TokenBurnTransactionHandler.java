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

import javax.inject.Named;
import lombok.AllArgsConstructor;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.ContractResultService;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@AllArgsConstructor
@Named
class TokenBurnTransactionHandler implements TransactionHandler {
    protected final ContractResultService contractResultService;
    protected final EntityProperties entityProperties;

    @Override
    public ContractResult getContractResult(Transaction transaction, RecordItem recordItem) {
        if (entityProperties.getPersist().isContractsPrecompileResults() && recordItem.getRecord()
                .hasContractCallResult()) {
            return contractResultService.getContractResult(recordItem);
        }

        return null;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenBurn().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENBURN;
    }
}
