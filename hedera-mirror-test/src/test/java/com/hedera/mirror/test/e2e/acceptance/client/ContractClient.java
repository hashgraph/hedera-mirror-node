package com.hedera.mirror.test.e2e.acceptance.client;

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
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.support.RetryTemplate;

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

@Named
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ContractClient extends AbstractNetworkClient {
    public ContractClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        log.debug("Creating Contract Client");
    }

    public NetworkTransactionResponse createContract(FileId fileId, long gas, Hbar payableAmount,
                                                     ContractFunctionParameters contractFunctionParameters) {
        log.debug("Create new contract");
        String memo = getMemo("Create contract");
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

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(contractCreateTransaction);
        ContractId contractId = networkTransactionResponse.getReceipt().contractId;
        log.debug("Created new contract {}", contractId);

        TransactionRecord transactionRecord = getTransactionRecord(networkTransactionResponse.getTransactionId());
        logContractFunctionResult("constructor", transactionRecord.contractFunctionResult);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateContract(ContractId contractId) {
        log.debug("Update contract {}", contractId);
        String memo = getMemo("Update contract");
        ContractUpdateTransaction contractUpdateTransaction = new ContractUpdateTransaction()
                .setContractId(contractId)
                .setContractMemo(memo)
                .setTransactionMemo(memo);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(contractUpdateTransaction);
        log.debug("Updated contract {}", contractId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse deleteContract(ContractId contractId, AccountId transferAccountId,
                                                     ContractId transferContractId) {
        log.debug("Delete contract {}", contractId);
        String memo = getMemo("Delete contract");
        ContractDeleteTransaction contractDeleteTransaction = new ContractDeleteTransaction()
                .setContractId(contractId)
                .setTransactionMemo(memo);

        // either AccountId or ContractId, not both
        if (transferAccountId != null) {
            contractDeleteTransaction.setTransferAccountId(transferAccountId);
        }

        if (transferContractId != null) {
            contractDeleteTransaction.setTransferContractId(transferContractId);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(contractDeleteTransaction);
        log.debug("Deleted contract {}", contractId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse executeContract(ContractId contractId, long gas, String functionName,
                                                      ContractFunctionParameters parameters, Hbar payableAmount) {
        log.debug("Call contract {}'s function {}", contractId, functionName);

        ContractExecuteTransaction contractExecuteTransaction = new ContractExecuteTransaction()
                .setContractId(contractId)
                .setGas(gas)
                .setTransactionMemo(getMemo("Execute contract"))
                .setMaxTransactionFee(Hbar.from(100));

        if (parameters == null) {
            contractExecuteTransaction.setFunction(functionName);
        } else {
            contractExecuteTransaction.setFunction(functionName, parameters);
        }

        if (payableAmount != null) {
            contractExecuteTransaction.setPayableAmount(payableAmount);
        }

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(contractExecuteTransaction);

        TransactionRecord transactionRecord = getTransactionRecord(networkTransactionResponse.getTransactionId());
        logContractFunctionResult(functionName, transactionRecord.contractFunctionResult);

        return networkTransactionResponse;
    }

    private void logContractFunctionResult(String functionName, ContractFunctionResult contractFunctionResult) {
        if (contractFunctionResult == null) {
            return;
        }

        log.trace("ContractFunctionResult for function {}, contractId: {}, gasUsed: {}, logCount: {}",
                functionName,
                contractFunctionResult.contractId,
                contractFunctionResult.gasUsed,
                contractFunctionResult.logs.size());
    }
}
