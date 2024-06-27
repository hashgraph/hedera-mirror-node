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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
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
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthBlock.Block;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthSyncing;
import org.web3j.protocol.core.methods.response.EthSyncing.Result;
import org.web3j.protocol.core.methods.response.NetVersion;
import org.web3j.protocol.websocket.events.Notification;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

@SuppressWarnings({"rawtypes", "unchecked"})
public class TestWeb3jService implements Web3jService {

    private static final Long GAS_LIMIT = 15_000_000L;
    private static final String MOCK_KEY = "0x4e3c5c727f3f4b8f8e8a8fe7e032cf78b8693a2b711e682da1d3a26a6a3b58b6";

    private final ContractCallService contractCallService;
    private final ContractGasProvider contractGasProvider;
    private final Credentials credentials;
    private final DomainBuilder domainBuilder;
    private final Map<String, String> trxResMap = new HashMap<>();
    private final Web3j web3j;

    private Address sender = Address.fromHexString("");

    public TestWeb3jService(ContractCallService contractCallService, DomainBuilder domainBuilder) {
        this.contractCallService = contractCallService;
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

    @SneakyThrows(Exception.class)
    public <T extends Contract> T deploy(Deployer<T> deployer) {
        return deployer.deploy(web3j, credentials, contractGasProvider).send();
    }

    @Override
    public <T extends Response> T send(Request request, Class<T> responseType) throws IOException {
        final var method = request.getMethod();
        return switch (method) {
            case "eth_call" -> (T) ethCall(request.getParams(), request);
            case "eth_getBlockByNumber" -> (T) ethGetBlockByNumber();
            case "eth_getTransactionCount" -> (T) ethGetTransactionCount();
            case "eth_getTransactionReceipt" -> (T) getTransactionReceipt(request);
            case "eth_sendRawTransaction" -> (T) call(request.getParams(), request);
            case "eth_syncing" -> (T) ethSyncing();
            case "net_version" -> (T) netVersion();
            default -> throw new UnsupportedOperationException(request.getMethod());
        };
    }

    private EthSendTransaction call(List<?> params, Request request) {
        var rawTransaction = TransactionDecoder.decode(params.get(0).toString());
        var trxHex = generateTransactionHashHexEncoded(rawTransaction, credentials);
        final var to = rawTransaction.getTo();

        if (to.equals("0x")) {
            return sendTopLevelContractCreate(rawTransaction, trxHex, request);
        }

        return sendEthCall(rawTransaction, trxHex, request);
    }

    private EthSendTransaction sendTopLevelContractCreate(
            RawTransaction rawTrxDecoded, String trxHex, Request request) {
        final var res = new EthSendTransaction();
        var serviceParameters = serviceParametersForTopLevelContractCreate(rawTrxDecoded.getData(), ETH_CALL, sender);
        final var mirrorNodeResult = contractCallService.processCall(serviceParameters);

        try {
            final var contractInstance = this.deploy(mirrorNodeResult);
            res.setResult(trxHex);
            res.setRawResponse(contractInstance.toHexString());
            res.setId(request.getId());
            res.setJsonrpc(request.getJsonrpc());
            trxResMap.put(trxHex, contractInstance.toHexString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return res;
    }

    private EthSendTransaction sendEthCall(RawTransaction rawTrxDecoded, String trxHex, Request request) {
        final var res = new EthSendTransaction();
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

        final var mirrorNodeResult = contractCallService.processCall(serviceParameters);
        res.setResult(trxHex);
        res.setRawResponse(mirrorNodeResult);
        res.setId(request.getId());
        res.setJsonrpc(request.getJsonrpc());

        trxResMap.put(trxHex, mirrorNodeResult);

        return res;
    }

    private EthCall ethCall(List<Transaction> reqParams, Request request) {
        var transaction = reqParams.get(0);

        final var serviceParameters = serviceParametersForExecutionSingle(
                Bytes.fromHexString(transaction.getData()),
                Address.fromHexString(transaction.getTo()),
                ETH_CALL,
                transaction.getValue() != null ? Long.parseLong(transaction.getValue()) : 0L,
                BlockType.LATEST,
                GAS_LIMIT,
                sender);
        final var result = contractCallService.processCall(serviceParameters);

        final var ethCall = new EthCall();
        ethCall.setId(request.getId());
        ethCall.setJsonrpc(request.getJsonrpc());
        ethCall.setResult(result);
        return ethCall;
    }

    private EthSyncing ethSyncing() {
        var result = new Result();
        result.setSyncing(false);

        var ethSyncing = new EthSyncing();
        ethSyncing.setResult(result);
        return ethSyncing;
    }

    private EthBlock ethGetBlockByNumber() {
        var block = new Block();
        block.setTimestamp(String.valueOf(System.currentTimeMillis()));

        var ethBlock = new EthBlock();
        ethBlock.setResult(block);
        return ethBlock;
    }

    private NetVersion netVersion() {
        var netVersion = new NetVersion();
        netVersion.setResult("1");
        return netVersion;
    }

    private EthGetTransactionCount ethGetTransactionCount() {
        var ethGetTransactionCount = new EthGetTransactionCount();
        ethGetTransactionCount.setResult("1");
        return ethGetTransactionCount;
    }

    private EthGetTransactionReceipt getTransactionReceipt(Request request) {
        final var trxHash = request.getParams().get(0).toString();
        final var res = new EthGetTransactionReceipt();
        var mockReceipt = new TransactionReceiptCustom();
        mockReceipt.setTransactionHash(trxHash);
        mockReceipt.setData(trxResMap.get(trxHash));
        mockReceipt.setContractAddress(trxResMap.get(trxHash));
        res.setResult(mockReceipt);

        return res;
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
        final var sender = new HederaEvmAccount(senderAddress);

        final var callData = Bytes.wrap(Hex.decode(contractInitCode));
        return CallServiceParameters.builder()
                .sender(sender)
                .callData(callData)
                .receiver(Address.ZERO)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(BlockType.LATEST)
                .build();
    }

    public Address deploy(String binary) {
        final var id = domainBuilder.id();
        final var contractAddress = toAddress(EntityId.of(id));
        precompileContractPersist(binary, id);

        return contractAddress;
    }

    private void precompileContractPersist(String binary, long entityId) {
        final var contractBytes = Hex.decode(binary.replace("0x", ""));
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

    public interface Deployer<T extends Contract> {
        RemoteCall<T> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider);
    }
}
