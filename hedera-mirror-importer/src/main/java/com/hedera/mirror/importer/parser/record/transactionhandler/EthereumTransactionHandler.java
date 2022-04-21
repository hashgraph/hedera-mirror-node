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
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;

@Named
@RequiredArgsConstructor
class EthereumTransactionHandler implements TransactionHandler {
    private final EntityIdService entityIdService;

    /**
     * Attempts to extract the contract ID from the ethereumTransaction.
     *
     * @param recordItem to check
     * @return The contract ID associated with this ethereum transaction call
     */
    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var transactionRecord = recordItem.getRecord();

        // pull entity from ContractResult
        var contractID = transactionRecord.hasContractCreateResult() ?
                transactionRecord.getContractCreateResult().getContractID() :
                transactionRecord.getContractCallResult().getContractID();
        return entityIdService.lookup(contractID);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }

//    @Override
//    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
//        if (!entityProperties.getPersist().isEthereumTransactions()) {
//            return;
//        }
//
//        var body = recordItem.getTransactionBody().getEthereumTransaction();
//
//        // FIX: In some cases initCode is located in FileID and needs to be extracted from record
//
//        var ethereumTransaction = ethereumTransactionParser.parse(body);
//        if (ethereumTransaction == null) {
//            return;
//        }
//
//        // if to is null create Contract
//        if (ethereumTransaction.getToAddress() == null) {
//
//        }
//
//        // update ethereumTransaction with record values
//        ethereumTransaction.setConsensusTimestamp(recordItem.getConsensusTimestamp());
//        ethereumTransaction.setHash(DomainUtils.toBytes(recordItem.getRecord().getEthereumHash()));
//        ethereumTransaction.setPayerAccountId(recordItem.getPayerAccountId());
////            ethereumTransaction.setFromAddress(); // full from signature
//        entityListener.onEthereumTransaction(ethereumTransaction);
//    }
}
