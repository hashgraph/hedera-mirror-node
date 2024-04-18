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

package com.hedera.mirror.test.e2e.acceptance.client;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.hashgraph.sdk.*;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.util.ethereum.EthTxData;
import com.hedera.mirror.test.e2e.acceptance.util.ethereum.EthTxSigs;
import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.retry.support.RetryTemplate;

@Named
public class EthereumClient extends AbstractNetworkClient {

    private final Collection<ContractId> contractIds = new CopyOnWriteArrayList<>();

    private final HashMap<PrivateKey, Integer> accountNonce = new HashMap<>();

    public EthereumClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    @Override
    public void clean() {
        // can't delete ethereum contracts, they are immutable
        log.info("Deleting {} contracts", contractIds.size());
    }

    private final TupleType LONG_TUPLE = TupleType.parse("(int64)");

    protected byte[] gasLongToBytes(final Long gas) {
        return Bytes.wrap(LONG_TUPLE.encode(Tuple.of(gas)).array()).toArray();
    }

    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    private BigInteger maxFeePerGas = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(50L));
    private BigInteger gasPrice = WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(50L));

    public NetworkTransactionResponse createContract(
            PrivateKey signerKey,
            FileId fileId,
            String fileContents,
            long gas,
            Hbar payableAmount,
            ContractFunctionParameters contractFunctionParameters) {

        int nonce = getNonce(signerKey);
        byte[] chainId = Integers.toBytes(298);
        byte[] maxPriorityGas = gasLongToBytes(20_000L);
        byte[] maxGas = gasLongToBytes(maxFeePerGas.longValueExact());
        byte[] to = new byte[] {};
        BigInteger value = payableAmount != null
                ? WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(payableAmount.toTinybars()))
                : BigInteger.ZERO;
        // FUTURE - construct bytecode with constructor arguments
        byte[] callData = Bytes.fromHexString(fileContents).toArray();

        var ethTxData = new EthTxData(
                null,
                EthTxData.EthTransactionType.EIP1559,
                chainId,
                nonce,
                gasLongToBytes(gasPrice.longValueExact()),
                maxPriorityGas,
                maxGas,
                gas, // gasLimit
                to, // to
                value, // value
                callData,
                new byte[] {}, // accessList
                0,
                null,
                null,
                null);

        var signedEthTxData = EthTxSigs.signMessage(ethTxData, signerKey);
        signedEthTxData = signedEthTxData.replaceCallData(new byte[] {});

        EthereumTransaction ethereumTransaction = new EthereumTransaction()
                .setCallDataFileId(fileId)
                .setMaxGasAllowanceHbar(Hbar.from(100L))
                .setEthereumData(signedEthTxData.encodeTx());

        var memo = getMemo("Create contract");

        var response = executeTransactionAndRetrieveReceipt(ethereumTransaction, null, null);
        var contractId = response.getReceipt().contractId;
        log.info("Created new contract {} with memo '{}' via {}", contractId, memo, response.getTransactionId());

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult("constructor", transactionRecord.contractFunctionResult);
        contractIds.add(contractId);
        incrementNonce(signerKey);
        return response;
    }

    public ExecuteContractResult executeContract(
            PrivateKey signerKey,
            ContractId contractId,
            long gas,
            String functionName,
            ContractFunctionParameters functionParameters,
            Hbar payableAmount,
            EthTxData.EthTransactionType type) {

        int nonce = getNonce(signerKey);
        byte[] chainId = Integers.toBytes(298);
        byte[] maxPriorityGas = gasLongToBytes(20_000L);
        byte[] maxGas = gasLongToBytes(maxFeePerGas.longValueExact());
        final var address = contractId.toSolidityAddress();
        final var addressBytes = Bytes.fromHexString(address.startsWith("0x") ? address : "0x" + address);
        byte[] to = addressBytes.toArray();
        var parameters = functionParameters != null ? functionParameters : new ContractFunctionParameters();
        byte[] callData = new ContractExecuteTransaction()
                .setFunction(functionName, parameters)
                .getFunctionParameters()
                .toByteArray();

        BigInteger value = payableAmount != null ? payableAmount.getValue().toBigInteger() : BigInteger.ZERO;

        var ethTxData = new EthTxData(
                null,
                type,
                chainId,
                nonce,
                gasLongToBytes(gasPrice.longValueExact()),
                maxPriorityGas,
                maxGas,
                gas, // gasLimit
                to, // to
                value, // value
                callData,
                new byte[] {}, // accessList
                0,
                null,
                null,
                null);

        var signedEthTxData = EthTxSigs.signMessage(ethTxData, signerKey);
        EthereumTransaction ethereumTransaction = new EthereumTransaction()
                .setMaxGasAllowanceHbar(Hbar.from(100L))
                .setEthereumData(signedEthTxData.encodeTx());

        var response = executeTransactionAndRetrieveReceipt(ethereumTransaction, null, null);

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult(functionName, transactionRecord.contractFunctionResult);

        log.info("Called contract {} function {} via {}", contractId, functionName, response.getTransactionId());
        incrementNonce(signerKey);
        return new ExecuteContractResult(transactionRecord.contractFunctionResult, response);
    }

    private void logContractFunctionResult(String functionName, ContractFunctionResult contractFunctionResult) {
        if (contractFunctionResult == null) {
            return;
        }

        log.trace(
                "ContractFunctionResult for function {}, contractId: {}, gasUsed: {}, logCount: {}",
                functionName,
                contractFunctionResult.contractId,
                contractFunctionResult.gasUsed,
                contractFunctionResult.logs.size());
    }

    @RequiredArgsConstructor
    public enum NodeNameEnum {
        CONSENSUS("consensus"),
        MIRROR("mirror");

        private final String name;

        static Optional<NodeNameEnum> of(String name) {
            try {
                return Optional.ofNullable(name).map(NodeNameEnum::valueOf);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    public String getClientAddress() {
        return sdkClient.getClient().getOperatorAccountId().toSolidityAddress();
    }

    public record ExecuteContractResult(
            ContractFunctionResult contractFunctionResult, NetworkTransactionResponse networkTransactionResponse) {}

    private Integer getNonce(PrivateKey accountKey) {
        return accountNonce.getOrDefault(accountKey, 0);
    }

    private void incrementNonce(PrivateKey accountKey) {
        if (accountNonce.containsKey(accountKey)) {
            accountNonce.put(accountKey, accountNonce.get(accountKey) + 1);
        } else {
            accountNonce.put(accountKey, 1);
        }
    }
}
