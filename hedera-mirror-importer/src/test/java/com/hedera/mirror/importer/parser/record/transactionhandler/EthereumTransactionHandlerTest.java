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

import static com.hedera.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS;
import static com.hedera.mirror.common.converter.WeiBarTinyBarConverter.WEIBARS_TO_TINYBARS_BIGINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.math.BigInteger;
import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionParser;

class EthereumTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private static final ByteString ETHEREUM_HASH = DomainUtils.fromBytes(RandomUtils.nextBytes(32));

    @Mock(lenient = true)
    protected EthereumTransactionParser ethereumTransactionParser;

    private EntityProperties entityProperties;

    @Override
    protected TransactionHandler getTransactionHandler() {
        doReturn(domainBuilder.ethereumTransaction(true).get()).when(ethereumTransactionParser).decode(any());
        entityProperties = new EntityProperties();
        return new EthereumTransactionHandler(entityProperties, entityListener, ethereumTransactionParser);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder().setEthereumTransaction(EthereumTransactionBody.newBuilder().build());
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return TransactionRecord.newBuilder()
                .setConsensusTimestamp(MODIFIED_TIMESTAMP)
                .setReceipt(getTransactionReceipt(ResponseCodeEnum.SUCCESS))
                .setContractCallResult(recordItemBuilder.contractFunctionResult(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Test
    void getType() {
        assertThat(transactionHandler.getType()).isEqualTo(TransactionType.ETHEREUMTRANSACTION);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void getEntityId(boolean create) {
        var recordItem = recordItemBuilder.ethereumTransaction(create).build();
        var functionResult = getContractFunctionResult(recordItem.getTransactionRecord(), create);
        var expectedId = EntityId.of(functionResult.getContractID());

        assertThat(transactionHandler.getEntity(recordItem)).isEqualTo(expectedId);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void updateTransaction(boolean create) {
        var fileId = EntityId.of(999L, EntityType.FILE);
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        var gasLimit = ethereumTransaction.getGasLimit();
        var expectedValue = new BigInteger(ethereumTransaction.getValue())
                .divide(WEIBARS_TO_TINYBARS_BIGINT).toByteArray();
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder.ethereumTransaction(create)
                .record(x -> x.setEthereumHash(ETHEREUM_HASH))
                .transactionBody(b -> b.setCallData(FileID.newBuilder().setFileNum(fileId.getEntityNum())))
                .build();

        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);

        var body = recordItem.getTransactionBody().getEthereumTransaction();
        verify(entityListener).onEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction)
                .returns(fileId, EthereumTransaction::getCallDataId)
                .returns(recordItem.getConsensusTimestamp(), EthereumTransaction::getConsensusTimestamp)
                .returns(DomainUtils.toBytes(body.getEthereumData()), EthereumTransaction::getData)
                .returns(gasLimit , EthereumTransaction::getGasLimit)
                .returns(DomainUtils.toBytes(ETHEREUM_HASH), EthereumTransaction::getHash)
                .returns(body.getMaxGasAllowance(), EthereumTransaction::getMaxGasAllowance)
                .returns(recordItem.getPayerAccountId(), EthereumTransaction::getPayerAccountId)
                .returns(expectedValue, EthereumTransaction::getValue);

        assertThat(recordItem.getEthereumTransaction()).isSameAs(ethereumTransaction);

        var functionResult = getContractFunctionResult(recordItem.getTransactionRecord(), create);
        var senderId = functionResult.getSenderId().getAccountNum();
        verify(entityListener).onEntity(argThat(e -> e.getId() == senderId && e.getTimestampRange() == null &&
                e.getEthereumNonce() == ethereumTransaction.getNonce() + 1));
    }

    @Test
    void updateTransactionMissingSender() {
        boolean create = true;
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder.ethereumTransaction(create)
                .record(x -> x.getContractCreateResultBuilder().clearSenderId())
                .build();

        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onEthereumTransaction(any());
        verify(entityListener, never()).onEntity(any());
    }

    @Test
    void updateTransactionEmptyWeiBars() {
        boolean create = true;
        var emptyBytes = new byte[] {};
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        ethereumTransaction.setGasLimit(null);
        ethereumTransaction.setGasPrice(emptyBytes);
        ethereumTransaction.setMaxFeePerGas(emptyBytes);
        ethereumTransaction.setMaxPriorityFeePerGas(emptyBytes);
        ethereumTransaction.setValue(emptyBytes);
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder.ethereumTransaction(create).build();

        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onEthereumTransaction(ethereumTransaction);
        assertThat(ethereumTransaction)
                .returns(null, EthereumTransaction::getGasLimit)
                .returns(emptyBytes, EthereumTransaction::getGasPrice)
                .returns(emptyBytes, EthereumTransaction::getMaxFeePerGas)
                .returns(emptyBytes, EthereumTransaction::getMaxPriorityFeePerGas)
                .returns(emptyBytes, EthereumTransaction::getValue);
    }

    @ValueSource(booleans = {true, false})
    @ParameterizedTest
    void updateTransactionSkipNonceOnFailure(boolean create) {
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder.ethereumTransaction(create)
                .record(x -> x.clearContractCreateResult().clearContractCallResult())
                .status(ResponseCodeEnum.WRONG_NONCE)
                .build();

        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);

        verify(entityListener).onEthereumTransaction(any());
        verify(entityListener, never()).onEntity(any());
    }

    @Test
    void updateTransactionUpdateNonceOnFailure() {
        boolean create = true;
        var ethereumTransaction = domainBuilder.ethereumTransaction(create).get();
        doReturn(ethereumTransaction).when(ethereumTransactionParser).decode(any());

        var recordItem = recordItemBuilder.ethereumTransaction(create)
                .record(x -> x.setEthereumHash(ETHEREUM_HASH))
                .status(ResponseCodeEnum.INSUFFICIENT_GAS)
                .build();

        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);

        var functionResult = getContractFunctionResult(recordItem.getTransactionRecord(), create);
        var senderId = functionResult.getSenderId().getAccountNum();
        verify(entityListener).onEthereumTransaction(ethereumTransaction);
        verify(entityListener).onEntity(argThat(e -> e.getId() == senderId && e.getTimestampRange() == null &&
                e.getEthereumNonce() == ethereumTransaction.getNonce() + 1));
    }

    @Test
    void updateTransactionInvalid() {
        var recordItem = recordItemBuilder.ethereumTransaction(true).build();
        var transaction = new Transaction();
        doThrow(InvalidDatasetException.class).when(ethereumTransactionParser).decode(any());

        assertThatThrownBy(() -> transactionHandler.updateTransaction(transaction, recordItem))
                .isInstanceOf(InvalidDatasetException.class);
        verify(entityListener, never()).onEntity(any());
        verify(entityListener, never()).onEthereumTransaction(any());
    }

    @Test
    void updateTransactionDisabled() {
        entityProperties.getPersist().setEthereumTransactions(false);
        var recordItem = recordItemBuilder.ethereumTransaction(true).build();

        transactionHandler.updateTransaction(new Transaction(), recordItem);

        // verify parse and listener are never called
        verify(entityListener, never()).onEntity(any());
        verify(entityListener, never()).onEthereumTransaction(any());
        verify(ethereumTransactionParser, never()).decode(any());
    }

    private ContractFunctionResult getContractFunctionResult(TransactionRecord record, boolean create) {
        return create ? record.getContractCreateResult() : record.getContractCallResult();
    }
}
