/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.web3j;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hedera.mirror.web3.utils.ContractCallTestUtil.TRANSACTION_GAS_LIMIT;
import static com.hedera.mirror.web3.validation.HexValidator.HEX_PREFIX;
import static org.web3j.crypto.TransactionUtils.generateTransactionHashHexEncoded;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.service.ContractExecutionService;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import io.reactivex.Flowable;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.websocket.events.Notification;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

@SuppressWarnings({"rawtypes", "unchecked"})
@Named
public class TestWeb3jService implements Web3jService {
    private static final long DEFAULT_TRANSACTION_VALUE = 10L;
    private static final String MOCK_KEY = "0x4e3c5c727f3f4b8f8e8a8fe7e032cf78b8693a2b711e682da1d3a26a6a3b58b6";

    private final ContractExecutionService contractExecutionService;
    private final ContractGasProvider contractGasProvider;
    private final Credentials credentials;
    private final DomainBuilder domainBuilder;
    private final Web3j web3j;
    private Address sender = Address.fromHexString("");
    private String output;
    private boolean isEstimateGas = false;
    private String transactionResult;

    public TestWeb3jService(ContractExecutionService contractExecutionService, DomainBuilder domainBuilder) {
        this.contractExecutionService = contractExecutionService;
        this.contractGasProvider = new DefaultGasProvider();
        this.credentials = Credentials.create(ECKeyPair.create(Numeric.hexStringToByteArray(MOCK_KEY)));
        this.domainBuilder = domainBuilder;
        this.web3j = Web3j.build(this);
    }

    public void setSender(Address sender) {
        this.sender = sender;
    }

    public void setSender(String sender) {
        this.sender = Address.fromHexString(sender);
    }

    public String getOutput() {
        return output;
    }

    public void setEstimateGas(final boolean isEstimateGas) {
        this.isEstimateGas = isEstimateGas;
    }

    @SneakyThrows(Exception.class)
    public <T extends Contract> T deploy(Deployer<T> deployer) {
        return deployer.deploy(web3j, credentials, contractGasProvider).send();
    }

    @Override
    public <T extends Response> T send(Request request, Class<T> responseType) {
        final var method = request.getMethod();
        return switch (method) {
            case "eth_call" -> (T) ethCall(request.getParams(), request);
            case "eth_getTransactionCount" -> (T) ethGetTransactionCount();
            case "eth_getTransactionReceipt" -> (T) getTransactionReceipt(request);
            case "eth_sendRawTransaction" -> (T) call(request.getParams(), request);
            default -> throw new UnsupportedOperationException(method);
        };
    }

    private EthSendTransaction call(List<?> params, Request request) {
        var rawTransaction = TransactionDecoder.decode(params.get(0).toString());
        var transactionHash = generateTransactionHashHexEncoded(rawTransaction, credentials);
        final var to = rawTransaction.getTo();

        if (to.equals(HEX_PREFIX)) {
            return sendTopLevelContractCreate(rawTransaction, transactionHash, request);
        }

        return sendEthCall(rawTransaction, transactionHash, request);
    }

    private EthSendTransaction sendTopLevelContractCreate(
            RawTransaction rawTransaction, String transactionHash, Request request) {
        final var res = new EthSendTransaction();
        var serviceParameters = serviceParametersForTopLevelContractCreate(rawTransaction.getData(), ETH_CALL, sender);
        final var mirrorNodeResult = contractExecutionService.processCall(serviceParameters);

        try {
            final var contractInstance = this.deployInternal(mirrorNodeResult);
            res.setResult(transactionHash);
            res.setRawResponse(contractInstance.toHexString());
            res.setId(request.getId());
            res.setJsonrpc(request.getJsonrpc());
            transactionResult = contractInstance.toHexString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return res;
    }

    private EthSendTransaction sendEthCall(RawTransaction rawTransaction, String transactionHash, Request request) {
        final var res = new EthSendTransaction();
        var serviceParameters = serviceParametersForExecutionSingle(
                Bytes.fromHexString(rawTransaction.getData()),
                Address.fromHexString(rawTransaction.getTo()),
                isEstimateGas ? ETH_ESTIMATE_GAS : ETH_CALL,
                rawTransaction.getValue().longValue() >= 0
                        ? rawTransaction.getValue().longValue()
                        : DEFAULT_TRANSACTION_VALUE,
                BlockType.LATEST,
                TRANSACTION_GAS_LIMIT,
                sender);

        final var mirrorNodeResult = contractExecutionService.processCall(serviceParameters);
        res.setResult(transactionHash);
        res.setRawResponse(mirrorNodeResult);
        res.setId(request.getId());
        res.setJsonrpc(request.getJsonrpc());

        transactionResult = mirrorNodeResult;
        output = mirrorNodeResult;
        return res;
    }

    private EthCall ethCall(List<Transaction> reqParams, Request request) {
        var transaction = reqParams.get(0);

        final var serviceParameters = serviceParametersForExecutionSingle(
                Bytes.fromHexString(transaction.getData()),
                Address.fromHexString(transaction.getTo()),
                isEstimateGas ? ETH_ESTIMATE_GAS : ETH_CALL,
                transaction.getValue() != null ? Long.parseLong(transaction.getValue()) : 0L,
                BlockType.LATEST,
                TRANSACTION_GAS_LIMIT,
                sender);
        final var result = contractExecutionService.processCall(serviceParameters);
        output = result;

        final var ethCall = new EthCall();
        ethCall.setId(request.getId());
        ethCall.setJsonrpc(request.getJsonrpc());
        ethCall.setResult(result);
        return ethCall;
    }

    @Override
    @SneakyThrows
    public <T extends Response> CompletableFuture<T> sendAsync(Request request, Class<T> responseType) {
        return CompletableFuture.completedFuture(send(request, responseType));
    }

    @Override
    public BatchResponse sendBatch(BatchRequest batchRequest) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("sendBatch");
    }

    @Override
    public CompletableFuture<BatchResponse> sendBatchAsync(BatchRequest batchRequest)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException("sendBatchAsync");
    }

    @Override
    public <T extends Notification<?>> Flowable<T> subscribe(
            Request request, String unsubscribeMethod, Class<T> responseType) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("subscribe");
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Close");
    }

    protected ContractExecutionParameters serviceParametersForExecutionSingle(
            final Bytes callData,
            final Address contractAddress,
            final CallServiceParameters.CallType callType,
            final long value,
            final BlockType block,
            final long gasLimit,
            final Address sender) {
        final var senderAccount = new HederaEvmAccount(sender);
        return ContractExecutionParameters.builder()
                .sender(senderAccount)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(gasLimit)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(block)
                .build();
    }

    protected ContractExecutionParameters serviceParametersForTopLevelContractCreate(
            final String contractInitCode, final CallServiceParameters.CallType callType, final Address senderAddress) {
        final var senderEvmAccount = new HederaEvmAccount(senderAddress);

        final var callData = Bytes.wrap(Hex.decode(contractInitCode));
        return ContractExecutionParameters.builder()
                .sender(senderEvmAccount)
                .callData(callData)
                .receiver(Address.ZERO)
                .gas(TRANSACTION_GAS_LIMIT)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(BlockType.LATEST)
                .build();
    }

    public Address deployInternal(String binary) {
        final var id = domainBuilder.id();
        final var contractAddress = toAddress(EntityId.of(id));
        contractPersist(binary, id);

        return contractAddress;
    }

    private void contractPersist(String binary, long entityId) {
        final var contractBytes = Hex.decode(binary.replace(HEX_PREFIX, ""));
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.type(CONTRACT).id(entityId).num(entityId).key(null))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(entity.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(entity.getId()))
                .persist();
    }

    private EthGetTransactionCount ethGetTransactionCount() {
        var ethGetTransactionCount = new EthGetTransactionCount();
        ethGetTransactionCount.setResult("1");
        return ethGetTransactionCount;
    }

    private EthGetTransactionReceipt getTransactionReceipt(Request request) {
        final var transactionHash = request.getParams().getFirst().toString();
        final var ethTransactionReceipt = new EthGetTransactionReceipt();
        final var transactionReceipt = new TransactionReceipt();
        transactionReceipt.setTransactionHash(transactionHash);
        if (isEstimateGas) {
            transactionReceipt.setGasUsed(transactionResult);
        } else {
            transactionReceipt.setContractAddress(transactionResult);
        }
        ethTransactionReceipt.setResult(transactionReceipt);

        return ethTransactionReceipt;
    }

    public interface Deployer<T extends Contract> {
        RemoteCall<T> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Web3jTestConfiguration {

        @Bean
        TestWeb3jService testWeb3jService(
                ContractExecutionService contractExecutionService, DomainBuilder domainBuilder) {
            return new TestWeb3jService(contractExecutionService, domainBuilder);
        }
    }
}
