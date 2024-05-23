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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.rest.model.TransactionTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hashgraph.sdk.*;
import com.hedera.mirror.rest.model.ContractResult;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import com.hedera.mirror.rest.model.TransactionDetail;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.EthereumClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;
import com.hedera.mirror.test.e2e.acceptance.util.ethereum.EthTxData;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@CustomLog
public class EthereumFeature extends BaseContractFeature {

    @Autowired
    protected EthereumClient ethereumClient;

    @Autowired
    protected AccountClient accountClient;

    protected AccountId ethereumSignerAccount;
    protected PrivateKey ethereumSignerPrivateKey;
    private String account;

    private byte[] childContractBytecodeFromParent;

    @Given("I successfully created a signer account with an EVM address alias")
    public void createAccountWithEvmAddressAlias() {
        ethereumSignerPrivateKey = PrivateKey.generateECDSA();
        ethereumSignerAccount = ethereumSignerPrivateKey.getPublicKey().toAccountId(0, 0);

        networkTransactionResponse = accountClient.sendCryptoTransfer(ethereumSignerAccount, Hbar.from(5L), null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("validate the signer account and its balance")
    public void verifyAccountCreated() {
        var accountInfo = mirrorClient.getAccountDetailsUsingAlias(ethereumSignerAccount);
        account = accountInfo.getAccount();
        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(TransactionDetail::getConsensusTimestamp))
                .toList();

        assertThat(accountInfo.getAccount()).isNotNull();
        assertThat(accountInfo.getBalance().getBalance())
                .isEqualTo(Hbar.from(5L).toTinybars());

        assertThat(accountInfo.getTransactions()).hasSize(1);
        assertThat(transactions).hasSize(2);

        var createAccountTransaction = transactions.get(0);
        var transferTransaction = transactions.get(1);

        assertThat(transferTransaction)
                .usingRecursiveComparison()
                .ignoringFields("assessedCustomFees")
                .isEqualTo(accountInfo.getTransactions().get(0));

        assertThat(createAccountTransaction.getName()).isEqualTo(CRYPTOCREATEACCOUNT);
        assertThat(createAccountTransaction.getConsensusTimestamp()).isEqualTo(accountInfo.getCreatedTimestamp());
    }

    @Given("I successfully create contract by Legacy ethereum transaction")
    public void createNewERCtestContract() {
        deployedParentContract = ethereumContractCreate(ContractResource.PARENT_CONTRACT);
    }

    @Then("the mirror node REST API should return status {int} for the eth contract creation transaction")
    public void verifyMirrorAPIContractCreationResponses(int status) {
        var mirrorTransaction = verifyEthereumContractCreate(mirrorClient, status, true);
        assertThat(mirrorTransaction.getEntityId())
                .isEqualTo(deployedParentContract.contractId().toString());
    }

    @Then("the mirror node REST API should return status {int} for the ethereum transaction")
    public void verifyMirrorAPIContractResponses(int status) {
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId())
                .isEqualTo(deployedParentContract.contractId().toString());
    }

    @Then("the mirror node REST API should verify the deployed contract entity by eth call")
    public void verifyDeployedContractMirror() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById();
        verifyContractExecutionResultsByTransactionId();
    }

    @Given("I successfully call function using EIP-1559 ethereum transaction")
    public void callContract() {
        ContractFunctionParameters parameters = new ContractFunctionParameters().addUint256(BigInteger.valueOf(1000));

        executeEthereumTransaction(
                deployedParentContract.contractId(),
                "createChild",
                parameters,
                null,
                EthTxData.EthTransactionType.EIP1559);
    }

    @Then("the mirror node REST API should verify the ethereum called contract function")
    public void verifyContractFunctionCallMirror() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById();
        verifyContractExecutionResultsByTransactionId();
    }

    @Given("I successfully call function using EIP-2930 ethereum transaction")
    public void getChildBytecode() {
        var executeContractResult = executeEthereumTransaction(
                deployedParentContract.contractId(), "getBytecode", null, null, EthTxData.EthTransactionType.EIP2930);

        childContractBytecodeFromParent =
                executeContractResult.contractFunctionResult().getBytes(0);
        assertNotNull(childContractBytecodeFromParent);
    }

    public DeployedContract ethereumContractCreate(ContractResource contractResource) {
        var resource = resourceLoader.getResource(contractResource.getPath());
        try (var in = resource.getInputStream()) {
            CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
            var fileContent = compiledSolidityArtifact.getBytecode().replaceFirst("0x", "");
            var fileId = persistContractBytes(fileContent);

            networkTransactionResponse = ethereumClient.createContract(
                    ethereumSignerPrivateKey,
                    fileId,
                    fileContent,
                    ethereumClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    contractResource.getInitialBalance() == 0
                            ? null
                            : Hbar.fromTinybars(contractResource.getInitialBalance()));
            ContractId contractId = verifyCreateContractNetworkResponse();
            return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
        } catch (IOException e) {
            log.warn("Issue creating contract: {}, ex: {}", contractResource, e);
            throw new RuntimeException(e);
        }
    }

    private EthereumClient.ExecuteContractResult executeEthereumTransaction(
            ContractId contractId,
            String functionName,
            ContractFunctionParameters parameters,
            Hbar payableAmount,
            EthTxData.EthTransactionType type) {

        EthereumClient.ExecuteContractResult executeContractResult = ethereumClient.executeContract(
                ethereumSignerPrivateKey,
                contractId,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                functionName,
                parameters,
                payableAmount,
                type);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertNotNull(executeContractResult.contractFunctionResult());

        return executeContractResult;
    }

    @Override
    protected void verifyContractExecutionResults(ContractResult contractResult) {
        super.verifyContractExecutionResults(contractResult);
        assertThat(contractResult.getFrom()).isEqualTo(FeatureInputHandler.evmAddress(AccountId.fromString(account)));
    }

    protected TransactionDetail verifyEthereumContractCreate(
            MirrorNodeClient mirrorClient, int status, boolean finalizeHollowAccount) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction;

        mirrorTransaction = finalizeHollowAccount ? transactions.get(1) : transactions.get(0);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);

        return mirrorTransaction;
    }
}
