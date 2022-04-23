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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionParser;

@Named
@RequiredArgsConstructor
class EthereumTransactionHandler implements TransactionHandler {
    private final EntityIdService entityIdService;
    private final EntityProperties entityProperties;
    private final EntityListener entityListener;
    private final EthereumTransactionParser ethereumTransactionParser;

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
        var contractFunctionResult = transactionRecord.hasContractCreateResult() ?
                transactionRecord.getContractCreateResult() : transactionRecord.getContractCallResult();

        EntityId contractId = null;
        EntityId senderId = null;
        EthereumTransaction ethereumTransaction = null;
        if (!contractFunctionResult.equals(ContractFunctionResult.getDefaultInstance())) {
            contractId = entityIdService.lookup(contractFunctionResult.getContractID());
            senderId = EntityId.of(contractFunctionResult.getSenderId());
            ethereumTransaction = insertEthereumTransaction(recordItem, senderId);
        }

        if (transactionRecord.hasContractCreateResult()) {
            insertContract(contractId, senderId, ethereumTransaction, recordItem.getConsensusTimestamp());
        }

        return contractId;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.ETHEREUMTRANSACTION;
    }

    private EthereumTransaction insertEthereumTransaction(RecordItem recordItem, EntityId senderId) {
        if (!entityProperties.getPersist().isEthereumTransactions()) {
            return null;
        }

        var body = recordItem.getTransactionBody().getEthereumTransaction();
        var ethereumDataBytes = DomainUtils.toBytes(body.getEthereumData());
        var ethereumTransaction = ethereumTransactionParser.parse(ethereumDataBytes);
        if (ethereumTransaction == null) {
            return null;
        }

        // update ethereumTransaction with body values
        var file = body.getCallData() == FileID.getDefaultInstance() ? null : EntityId.of(body.getCallData());
        ethereumTransaction.setCallDataId(file);
        ethereumTransaction.setMaxGasAllowance(body.getMaxGasAllowance());

        // update ethereumTransaction with record values
        var transactionRecord = recordItem.getRecord();
        ethereumTransaction.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        ethereumTransaction.setData(ethereumDataBytes);
        ethereumTransaction.setHash(DomainUtils.toBytes(transactionRecord.getEthereumHash()));
        ethereumTransaction.setPayerAccountId(recordItem.getPayerAccountId());

        ethereumTransaction.setFromAddress(DomainUtils.toEvmAddress(senderId));

        entityListener.onEthereumTransaction(ethereumTransaction);
        return ethereumTransaction;
    }

    private void insertContract(EntityId contractId, EntityId senderId, EthereumTransaction ethereumTransaction,
                                long consensusTimestamp) {
        if (!entityProperties.getPersist().isContracts()) {
            return;
        }

        // persist newly created Contract since no ContractCreate is exposed
        if (contractId != null) {
            var publicKey = ethereumTransactionParser.retrievePublicKey(ethereumTransaction);
            var publicKeyBytes = Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(publicKey)).build()
                    .toByteArray();

            var contract = Contract.builder()
//                    .autoRenewPeriod(1800L)
                    .createdTimestamp(consensusTimestamp)
                    .deleted(false)
//                    .expirationTimestamp(timestamp + 30_000_000L)
                    .fileId(ethereumTransaction.getCallDataId())
                    .id(contractId.getId())
                    .key(publicKeyBytes)
                    .memo("") // missing memo, is hex data from transaction submission available?
                    .obtainerId(senderId)
//                    .proxyAccountId(entityId(ACCOUNT))
                    .num(contractId.getEntityNum())
                    .realm(contractId.getRealmNum())
                    .shard(contractId.getShardNum())
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .type(CONTRACT)
                    .build();
            entityListener.onContract(contract);
        }
    }
}
