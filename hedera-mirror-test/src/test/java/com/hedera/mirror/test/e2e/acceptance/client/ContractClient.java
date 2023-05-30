/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractCreateTransaction;
import com.hedera.hashgraph.sdk.ContractDeleteTransaction;
import com.hedera.hashgraph.sdk.ContractExecuteTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.ContractUpdateTransaction;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import jakarta.inject.Named;
import org.springframework.retry.support.RetryTemplate;

@Named
public class ContractClient extends AbstractNetworkClient {

    public ContractClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
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
        log.info("Deleted contract {} with memo '{}' via {}", contractId, memo, response.getTransactionId());
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

    public String getClientAddress() {
        return sdkClient.getClient().getOperatorAccountId().toSolidityAddress();
    }

    public record ExecuteContractResult(
            ContractFunctionResult contractFunctionResult, NetworkTransactionResponse networkTransactionResponse) {}
}
