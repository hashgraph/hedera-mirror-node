package com.hedera.mirror.importer.parser.record.transactionhandler;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.common.converter.WeiBarTinyBarConverter;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionParser;

@Log4j2
@Named
@RequiredArgsConstructor
class EthereumTransactionHandler implements TransactionHandler {
    private final EntityProperties entityProperties;
    private final EntityListener entityListener;
    private final EthereumTransactionParser ethereumTransactionParser;

    /**
     * Attempts to extract the contract ID from the ethereumTransaction.
     *
     * @param recordItem to check
     * @return The contract ID associated with this ethereum transaction call
     */
    @SuppressWarnings("deprecation")
    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();

        // pull entity from ContractResult
        var contractFunctionResult = transactionRecord.hasContractCreateResult() ?
                transactionRecord.getContractCreateResult() : transactionRecord.getContractCallResult();

        return EntityId.of(contractFunctionResult.getContractID());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isEthereumTransactions()) {
            return;
        }

        var body = recordItem.getTransactionBody().getEthereumTransaction();
        var ethereumDataBytes = DomainUtils.toBytes(body.getEthereumData());
        var ethereumTransaction = ethereumTransactionParser.decode(ethereumDataBytes);

        // update ethereumTransaction with body values
        if (body.hasCallData()) {
            ethereumTransaction.setCallDataId(EntityId.of(body.getCallData()));
        }

        // EVM logic uses weibar for gas values, convert transaction body gas values to tinybars
        convertGasWeiToTinyBars(ethereumTransaction);

        // update ethereumTransaction with record values
        var transactionRecord = recordItem.getTransactionRecord();
        ethereumTransaction.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        ethereumTransaction.setData(ethereumDataBytes);
        ethereumTransaction.setHash(DomainUtils.toBytes(transactionRecord.getEthereumHash()));
        ethereumTransaction.setMaxGasAllowance(body.getMaxGasAllowance());
        ethereumTransaction.setPayerAccountId(recordItem.getPayerAccountId());

        entityListener.onEthereumTransaction(ethereumTransaction);
        updateAccountNonce(recordItem, ethereumTransaction);
        recordItem.setEthereumTransaction(ethereumTransaction);
    }

    private void updateAccountNonce(RecordItem recordItem, EthereumTransaction ethereumTransaction) {
        var transactionRecord = recordItem.getTransactionRecord();

        // It should not update the nonce if it's unsuccessful and failed before EVM execution
        if (!recordItem.isSuccessful() && !transactionRecord.hasContractCallResult() && !transactionRecord.hasContractCreateResult()) {
            return;
        }

        var functionResult = transactionRecord.hasContractCreateResult() ? transactionRecord.getContractCreateResult() :
                transactionRecord.getContractCallResult();
        var senderId = EntityId.of(functionResult.getSenderId());

        if (!EntityId.isEmpty(senderId)) {
            Entity entity = senderId.toEntity();
            entity.setEthereumNonce(ethereumTransaction.getNonce() + 1);
            entity.setTimestampRange(null); // Don't trigger a history row
            entityListener.onEntity(entity);
        }
    }

    private void convertGasWeiToTinyBars(EthereumTransaction transaction) {
        var converter = WeiBarTinyBarConverter.INSTANCE;
        transaction.setGasPrice(converter.convert(transaction.getGasPrice(), false));
        transaction.setMaxFeePerGas(converter.convert(transaction.getMaxFeePerGas(), false));
        transaction.setMaxPriorityFeePerGas(converter.convert(transaction.getMaxPriorityFeePerGas(), false));
        transaction.setValue(converter.convert(transaction.getValue(), true));
    }
}
