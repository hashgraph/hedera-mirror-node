package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.time.Instant;
import java.util.concurrent.TimeoutException;
import javax.inject.Named;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.ContractCallQuery;
import com.hedera.hashgraph.sdk.ContractCreateTransaction;
import com.hedera.hashgraph.sdk.ContractDeleteTransaction;
import com.hedera.hashgraph.sdk.ContractExecuteTransaction;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.ContractInfo;
import com.hedera.hashgraph.sdk.ContractInfoQuery;
import com.hedera.hashgraph.sdk.ContractUpdateTransaction;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Named
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ContractClient extends AbstractNetworkClient {
    public ContractClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        super(sdkClient, retryTemplate);
        log.debug("Creating File Client");
    }

    public NetworkTransactionResponse createContract(FileId fileId, long gas,
                                                     ContractFunctionParameters contractFunctionParameters) {
        log.debug("Create new contract");
        String memo = String.format("Create contract %s", Instant.now());
        ContractCreateTransaction contractCreateTransaction = new ContractCreateTransaction()
                .setAdminKey(sdkClient.getExpandedOperatorAccountId().getPublicKey())
                .setGas(gas)
                .setConstructorParameters(contractFunctionParameters)
                .setBytecodeFileId(fileId)
                .setContractMemo(memo)
                .setTransactionMemo(memo);

        NetworkTransactionResponse networkTransactionResponse = executeTransactionAndRetrieveReceipt(
                contractCreateTransaction,
                KeyList.of(sdkClient.getExpandedOperatorAccountId().getPrivateKey()));
        ContractId contractId = networkTransactionResponse.getReceipt().contractId;
        log.debug("Created new contract {}", contractId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse updateContract(ContractId contractId, FileId fileId) {
        log.debug("Update contract {}", contractId);
        String memo = String.format("Update contract %s", Instant.now());
        ContractUpdateTransaction contractUpdateTransaction = new ContractUpdateTransaction()
                .setContractId(contractId)
                .setBytecodeFileId(fileId)
                .setContractMemo(memo)
                .setTransactionMemo(memo);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(contractUpdateTransaction);
        log.debug("Updated contract {}", contractId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse deleteContract(ContractId contractId) {
        log.debug("Delete contract {}", contractId);
        String memo = String.format("delete contract %s", Instant.now());
        ContractDeleteTransaction contractDeleteTransaction = new ContractDeleteTransaction()
                .setContractId(contractId)
                .setTransactionMemo(memo);

        NetworkTransactionResponse networkTransactionResponse =
                executeTransactionAndRetrieveReceipt(contractDeleteTransaction);
        log.debug("Deleted contract {}", contractId);

        return networkTransactionResponse;
    }

    public NetworkTransactionResponse callExecuteContract(ContractId contractId, long gas, String functionName,
                                                          ContractFunctionParameters parameters, Hbar payableAmount) {
        log.debug("Call contract function {}", functionName);

        String memo = String.format("Call contract %s", Instant.now());
        ContractExecuteTransaction contractExecuteTransaction = new ContractExecuteTransaction()
                .setContractId(contractId)
                .setGas(gas)
                .setTransactionMemo(memo);

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
        log.debug("Called contract {}", contractId);

        return networkTransactionResponse;
    }

    @SneakyThrows
    public ContractFunctionResult callContract(ContractId contractId, long gas, String functionName,
                                               ContractFunctionParameters parameters, Hbar maxQueryPayment) {
        log.debug("Call contract function {}", functionName);

        String memo = String.format("Call contract %s", Instant.now());
        ContractExecuteTransaction contractExecuteTransaction = new ContractExecuteTransaction()
                .setContractId(contractId)
                .setGas(gas)
                .setTransactionMemo(memo);

        if (parameters == null) {
            contractExecuteTransaction.setFunction(functionName);
        } else {
            contractExecuteTransaction.setFunction(functionName, parameters);
        }

        if (maxQueryPayment != null) {
            contractExecuteTransaction.setPayableAmount(maxQueryPayment);
        }

        return retryTemplate.execute(x -> {
            ContractCallQuery contractCallQuery = new ContractCallQuery()
                    .setContractId(contractId)
                    .setGas(gas);

            if (parameters == null) {
                contractCallQuery.setFunction(functionName);
            } else {
                contractCallQuery.setFunction(functionName, parameters);
            }

            if (maxQueryPayment != null) {
                contractCallQuery.setMaxQueryPayment(maxQueryPayment);
            }

            try {
                return contractCallQuery.execute(client);
            } catch (TimeoutException e) {
                log.error("TimeoutException: {}", e.getMessage());
            } catch (PrecheckStatusException e) {
                log.error("PrecheckStatusException: {}", e.getMessage());
            }
            return null;
        });
    }

    @SneakyThrows
    public ContractInfo getContractInfo(ContractId contractId) {
        return retryTemplate.execute(x -> new ContractInfoQuery()
                .setContractId(contractId)
                .execute(client));
    }
}
