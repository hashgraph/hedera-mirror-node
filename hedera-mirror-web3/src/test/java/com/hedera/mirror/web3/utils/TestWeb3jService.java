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

package com.hedera.mirror.web3.utils;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.web3j.crypto.TransactionUtils.generateTransactionHashHexEncoded;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.service.resources.TransactionReceiptCustom;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import io.reactivex.Flowable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.beans.factory.annotation.Autowired;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.BatchRequest;
import org.web3j.protocol.core.BatchResponse;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.websocket.events.Notification;
import org.web3j.tx.gas.ContractGasProvider;

@RequiredArgsConstructor
@SuppressWarnings({"rawtypes", "unchecked"})
public class TestWeb3jService implements Web3jService {
    private static final Long GAS_LIMIT = 15_000_000L;
    private static final String MOCK_RESULT = "1";
    private static final String EMPTY_BYTES = "0x";
    private Address sender = Address.fromHexString("");
    private ContractCallService contractCallService;
    private Map<String, String> transactionHexResultMap = new HashMap<>();

    @Autowired
    private Credentials credentials;

    @Autowired
    private DomainBuilder domainBuilder;

    @Autowired
    private ContractGasProvider contractGasProvider;

    public void setSender(Address sender) {
        this.sender = sender;
    }

    public void setSender(String sender) {
        this.sender = Address.fromHexString(sender);
    }

    public TestWeb3jService(ContractCallService contractCallService) {
        this.contractCallService = contractCallService;
    }

    @Override
    public <T extends Response> T send(Request request, Class<T> responseType) throws IOException {
        final var method = request.getMethod();
        return switch (method) {
            case "eth_syncing" -> (T) getEthSyncingRes();
            case "eth_getBlockByNumber" -> getEthBlockRes(responseType);
            case "net_version" -> getNetVersionRes(responseType);
            case "eth_getTransactionCount" -> getTransactionCountRes(responseType);
            case "eth_getTransactionReceipt" -> (T) getTransactionReceipt(request);
            case "eth_sendRawTransaction" -> (T) call(request.getParams(), request);
            case "eth_call" -> (T) ethCall(request.getParams(), request);
            default -> throw new UnsupportedOperationException(request.getMethod());
        };
    }

    private <T extends Response> T getResObj(Class<T> responseType) {
        try {
            return responseType.getDeclaredConstructor().newInstance();
        } catch (InstantiationException
                | IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private EthSendTransaction call(List reqParams, Request request) {
        var rawTrxDecoded = TransactionDecoder.decode(reqParams.get(0).toString());
        var trxHex = generateTransactionHashHexEncoded(rawTrxDecoded, credentials);
        final var to = rawTrxDecoded.getTo();

        if (to.equals(EMPTY_BYTES)) {
            return sendTopLevelContractCreate(rawTrxDecoded, trxHex, request);
        }

        return ethSendTransaction(rawTrxDecoded, trxHex, request);
    }

    private EthSendTransaction sendTopLevelContractCreate(
            RawTransaction rawTrxDecoded, String trxHex, Request request) {
        var serviceParameters = serviceParametersForTopLevelContractCreate(rawTrxDecoded.getData(), ETH_CALL, sender);
        final var result = contractCallService.processCall(serviceParameters);
        try {
            final var contractInstance = this.deploy(result);
            final var response = new EthSendTransaction();
            response.setResult(trxHex);
            response.setRawResponse(contractInstance.toHexString());
            response.setId(request.getId());
            response.setJsonrpc(request.getJsonrpc());
            transactionHexResultMap.put(trxHex, contractInstance.toHexString());

            return response;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private EthSendTransaction ethSendTransaction(
            RawTransaction rawTrxDecoded, String transactionHex, Request request) {
        final var response = new EthSendTransaction();
        var serviceParameters = serviceParametersForExecutionSingle(
                Bytes.fromHexString(rawTrxDecoded.getData()),
                Address.fromHexString(rawTrxDecoded.getTo()),
                ETH_CALL,
                rawTrxDecoded.getValue().longValue() >= 0
                        ? rawTrxDecoded.getValue().longValue()
                        : 10L,
                BlockType.LATEST,
                GAS_LIMIT,
                sender);

        final var result = contractCallService.processCall(serviceParameters);
        response.setResult(transactionHex);
        response.setRawResponse(result);
        response.setId(request.getId());
        response.setJsonrpc(request.getJsonrpc());

        transactionHexResultMap.put(transactionHex, result);

        return response;
    }

    private EthCall ethCall(List reqParams, Request request) {
        var transaction = (Transaction) reqParams.get(0);
        final var serviceParameters = serviceParametersForExecutionSingle(
                Bytes.fromHexString(transaction.getData()),
                Address.fromHexString(transaction.getTo()),
                ETH_CALL,
                transaction.getValue() != null ? Long.parseLong(transaction.getValue()) : 0L,
                BlockType.LATEST,
                GAS_LIMIT,
                sender);
        final var result = contractCallService.processCall(serviceParameters);
        final var response = new EthCall();
        response.setResult(result);
        response.setId(request.getId());
        response.setJsonrpc(request.getJsonrpc());

        return response;
    }

    private EthSyncing getEthSyncingRes() {
        var result = new EthSyncing.Result();
        result.setSyncing(false);

        var response = new EthSyncing();
        response.setResult(result);

        return response;
    }

    private <T extends Response> T getEthBlockRes(Class<T> responseType) {
        T response = getResObj(responseType);
        final var block = new EthBlock.Block();
        block.setTimestamp(String.valueOf(System.currentTimeMillis()));
        response.setResult(block);

        return response;
    }

    private <T extends Response> T getNetVersionRes(Class<T> responseType) {
        T response = getResObj(responseType);
        response.setResult(MOCK_RESULT);

        return response;
    }

    private <T extends Response> T getTransactionCountRes(Class<T> responseType) {
        T response = getResObj(responseType);
        response.setResult(MOCK_RESULT);

        return response;
    }

    private EthGetTransactionReceipt getTransactionReceipt(Request request) {
        final var transactionHex = request.getParams().get(0).toString();
        var mockReceipt = new TransactionReceiptCustom();
        mockReceipt.setTransactionHash(transactionHex);
        mockReceipt.setData(transactionHexResultMap.get(transactionHex));
        mockReceipt.setContractAddress(transactionHexResultMap.get(transactionHex));
        final var response = new EthGetTransactionReceipt();
        response.setResult(mockReceipt);

        return response;
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

    protected CallServiceParameters serviceParametersForExecutionSingle(
            final Bytes callData,
            final Address contractAddress,
            final CallServiceParameters.CallType callType,
            final long value,
            final BlockType block,
            final long gasLimit,
            final Address sender) {
        final var senderAccount = new HederaEvmAccount(sender);
        return CallServiceParameters.builder()
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

    protected CallServiceParameters serviceParametersForTopLevelContractCreate(
            final String contractInitCode, final CallServiceParameters.CallType callType, final Address senderAddress) {
        final var senderAccount = new HederaEvmAccount(senderAddress);

        final var callData = Bytes.wrap(Hex.decode(contractInitCode));
        return CallServiceParameters.builder()
                .sender(senderAccount)
                .callData(callData)
                .receiver(Address.ZERO)
                .gas(GAS_LIMIT)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(BlockType.LATEST)
                .build();
    }

    public Address deploy(String binary) {
        final var id = domainBuilder.id();
        final var contractAddress = toAddress(EntityId.of(id));
        contractPersist(binary, id);

        return contractAddress;
    }

    private void contractPersist(String binary, long entityId) {
        final var contractBytes = Hex.decode(binary.replace(EMPTY_BYTES, ""));
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.type(CONTRACT).id(entityId).num(entityId))
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
}
