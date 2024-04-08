/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.esaulpaugh.headlong.util.Integers;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractCallQuery;
import com.hedera.hashgraph.sdk.ContractCreateTransaction;
import com.hedera.hashgraph.sdk.ContractDeleteTransaction;
import com.hedera.hashgraph.sdk.ContractExecuteTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.ContractUpdateTransaction;
import com.hedera.hashgraph.sdk.EthereumTransaction;
import com.hedera.hashgraph.sdk.EthereumTransactionDataEip1559;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.util.TestUtil;
import jakarta.inject.Named;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.retry.support.RetryTemplate;

@Named
public class ContractClient extends AbstractNetworkClient {

    private final Collection<ContractId> contractIds = new CopyOnWriteArrayList<>();

    public ContractClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
    }

    @Override
    public void clean() {
        log.info("Deleting {} contracts", contractIds.size());
        deleteAll(contractIds, id -> deleteContract(id, client.getOperatorAccountId(), null));
    }

    public NetworkTransactionResponse createContract(
            FileId fileId, long gas, Hbar payableAmount, ContractFunctionParameters contractFunctionParameters) {
        var memo = getMemo("Create contract");
        ContractCreateTransaction contractCreateTransaction = new ContractCreateTransaction()
                .setAdminKey(sdkClient.getExpandedOperatorAccountId().getPublicKey())
                .setBytecodeFileId(fileId)
                .setContractMemo(memo)
                .setGas(gas)
                .setTransactionMemo(memo);

        if (contractFunctionParameters != null) {
            contractCreateTransaction.setConstructorParameters(contractFunctionParameters);
        }

        if (payableAmount != null) {
            contractCreateTransaction.setInitialBalance(payableAmount);
        }

        var response = executeTransactionAndRetrieveReceipt(contractCreateTransaction);
        var contractId = response.getReceipt().contractId;
        log.info("Created new contract {} with memo '{}' via {}", contractId, memo, response.getTransactionId());

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult("constructor", transactionRecord.contractFunctionResult);
        contractIds.add(contractId);

        return response;
    }

    public NetworkTransactionResponse updateContract(ContractId contractId) {
        var memo = getMemo("Update contract");
        ContractUpdateTransaction contractUpdateTransaction = new ContractUpdateTransaction()
                .setContractId(contractId)
                .setContractMemo(memo)
                .setTransactionMemo(memo);

        var response = executeTransactionAndRetrieveReceipt(contractUpdateTransaction);
        log.info("Updated contract {} with memo '{}' via {}", contractId, memo, response.getTransactionId());
        return response;
    }

    public NetworkTransactionResponse deleteContract(
            ContractId contractId, AccountId transferAccountId, ContractId transferContractId) {
        var memo = getMemo("Delete contract");
        ContractDeleteTransaction contractDeleteTransaction =
                new ContractDeleteTransaction().setContractId(contractId).setTransactionMemo(memo);

        // either AccountId or ContractId, not both
        if (transferAccountId != null) {
            contractDeleteTransaction.setTransferAccountId(transferAccountId);
        }

        if (transferContractId != null) {
            contractDeleteTransaction.setTransferContractId(transferContractId);
        }

        var response = executeTransactionAndRetrieveReceipt(contractDeleteTransaction);
        log.info("Deleted contract {} via {}", contractId, response.getTransactionId());
        contractIds.remove(contractId);
        return response;
    }

    public ExecuteContractResult executeContract(
            ContractId contractId,
            long gas,
            String functionName,
            ContractFunctionParameters parameters,
            Hbar payableAmount) {

        ContractExecuteTransaction contractExecuteTransaction = new ContractExecuteTransaction()
                .setContractId(contractId)
                .setGas(gas)
                .setTransactionMemo(getMemo("Execute contract"));

        if (parameters == null) {
            contractExecuteTransaction.setFunction(functionName);
        } else {
            contractExecuteTransaction.setFunction(functionName, parameters);
        }

        if (payableAmount != null) {
            contractExecuteTransaction.setPayableAmount(payableAmount);
        }

        var response = executeTransactionAndRetrieveReceipt(contractExecuteTransaction);

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult(functionName, transactionRecord.contractFunctionResult);

        log.info("Called contract {} function {} via {}", contractId, functionName, response.getTransactionId());
        return new ExecuteContractResult(transactionRecord.contractFunctionResult, response);
    }

    public static final BigInteger WEIBARS_TO_TINYBARS = BigInteger.valueOf(10_000_000_000L);
    public static final long DEFAULT_GAS_PRICE_TINYBARS = 50L;

    public ExecuteContractResult executeEthContract(
        ContractId contractId,
        long gas,
        String functionName,
        ContractFunctionParameters parameters,
        Hbar payableAmount) {

        Object[] params = new Object[] { };
        
        ContractExecuteTransaction contractExecuteTransaction = new ContractExecuteTransaction()
            .setContractId(contractId)
            .setGas(gas)
            .setTransactionMemo(getMemo("Execute contract"));

        if (parameters == null) {
            contractExecuteTransaction.setFunction(functionName);
        } else {
            contractExecuteTransaction.setFunction(functionName, parameters);
        }

        if (payableAmount != null) {
            contractExecuteTransaction.setPayableAmount(payableAmount);
        }

        contractExecuteTransaction.freezeWith(getSdkClient().getClient());
        
        final var ethTxData = new EthTxData(
            null,
            EthTransactionType.EIP1559,
            Integers.toBytes(296),
            1,
            gasLongToBytes(WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(DEFAULT_GAS_PRICE_TINYBARS)).longValueExact()),
            Integers.toBytes(1001),
            gasLongToBytes(WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(DEFAULT_GAS_PRICE_TINYBARS)).longValueExact()),
            gas,
            TestUtil.asSolidityAddress(contractId),
            WEIBARS_TO_TINYBARS.multiply(BigInteger.valueOf(4)),
            encodeParameters(params, ABI_EXAMPLE),
            //contractExecuteTransaction.getFunctionParameters().toByteArray(),
            new byte[] {},
            1,
            null,
            new byte[] {},
            new byte[] {});
    
        // TODO: add callData.length > MAX_CALL_DATA_SIZE case(see from HapiEthereumCall)
        
        EthereumTransactionDataEip1559 t = EthereumTransactionDataEip1559.fromBytes(ethTxData.encodeTx());
        EthereumTransaction ethereumTransaction = new EthereumTransaction()
            .setEthereumData(t.toBytes())
            .setMaxGasAllowanceHbar(Hbar.from(2));
            //.setTransactionId(contractExecuteTransaction.getTransactionId())
            //.setTransactionMemo("test-memo");

//        TransactionResponse txResponse;
//        TransactionReceipt receipt;
//        try {
//            txResponse = ethereumTransaction.execute(client);
//            receipt = txResponse.getReceipt(client);
//        } catch (Exception e) {
//            System.out.println("");
//        }

        System.out.println();

        // FOR DEBUG ONLY //
//        var response = executeTransaction(ethereumTransaction, null, null);
//        TransactionRecord transactionRecord = getTransactionRecordDebug(response);
        
        var response = executeTransactionAndRetrieveReceipt(ethereumTransaction);
        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());

        logContractFunctionResult(functionName, transactionRecord.contractFunctionResult);

        log.info("Called contract {} function {} via {}", contractId, functionName, response.getTransactionId());
        return new ExecuteContractResult(transactionRecord.contractFunctionResult, response);
    }

    private static byte[] encodeParameters(final Object[] params, final String abi) {
        byte[] callData = new byte[] {};
        if (!abi.isEmpty() && !abi.contains("<empty>")) {
            final var abiFunction = Function.fromJson(abi);
            callData = abiFunction.encodeCallWithArgs(params).array();
        }

        return callData;
    }
    
    private static final String ABI_EXAMPLE = """ 
{"inputs":[],"name":"getSender2","outputs":[{"internalType":"address","name":"","type":"address"}],"stateMutability":"view","type":"function"}
""";

    private final TupleType LONG_TUPLE = TupleType.parse("(int64)");

    protected byte[] gasLongToBytes(final Long gas) {
        return Bytes.wrap(LONG_TUPLE.encode(Tuple.of(gas)).array()).toArray();
    }
    
    public ExecuteContractResult executeContract(
            ContractId contractId, long gas, String functionName, byte[] parameters, Hbar payableAmount) {

        ContractExecuteTransaction contractExecuteTransaction = new ContractExecuteTransaction()
                .setContractId(contractId)
                .setGas(gas)
                .setTransactionMemo(getMemo("Execute contract"));

        if (parameters == null) {
            contractExecuteTransaction.setFunction(functionName);
        } else {
            contractExecuteTransaction.setFunctionParameters(ByteString.copyFrom(parameters));
        }

        if (payableAmount != null) {
            contractExecuteTransaction.setPayableAmount(payableAmount);
        }

        var response = executeTransactionAndRetrieveReceipt(contractExecuteTransaction);

        TransactionRecord transactionRecord = getTransactionRecord(response.getTransactionId());
        logContractFunctionResult(functionName, transactionRecord.contractFunctionResult);

        log.info("Called contract {} function {} via {}", contractId, functionName, response.getTransactionId());
        return new ExecuteContractResult(transactionRecord.contractFunctionResult, response);
    }

    public ContractFunctionResult executeContractQuery(
            ContractId contractId, String functionName, Long gas, byte[] data)
            throws PrecheckStatusException, TimeoutException {
        ContractCallQuery contractCallQuery =
                new ContractCallQuery().setContractId(contractId).setGas(gas);

        contractCallQuery.setFunctionParameters(data);

        long costInTinybars = contractCallQuery.getCost(client).toTinybars();

        long additionalTinybars = 10000;
        long totalPaymentInTinybars = costInTinybars + additionalTinybars;

        contractCallQuery.setQueryPayment(Hbar.fromTinybars(totalPaymentInTinybars));

        ContractFunctionResult functionResult = contractCallQuery.execute(client);

        log.info("Executed query on contract {} function {}, result: {}", contractId, functionName, functionResult);

        return functionResult;
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
}
