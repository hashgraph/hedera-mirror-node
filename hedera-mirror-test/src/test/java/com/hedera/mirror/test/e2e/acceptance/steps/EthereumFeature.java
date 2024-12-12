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

import static com.hedera.mirror.rest.model.TransactionTypes.CRYPTOCREATEACCOUNT;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PARENT_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CREATE_CHILD;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.GET_BYTE_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.web3j.crypto.transaction.type.TransactionType.EIP1559;
import static org.web3j.crypto.transaction.type.TransactionType.EIP2930;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.mirror.rest.model.ContractResult;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import com.hedera.mirror.rest.model.TransactionDetail;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.EthereumClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Objects;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.web3j.crypto.transaction.type.TransactionType;

@CustomLog
public class EthereumFeature extends AbstractEstimateFeature {

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

        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
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
        deployedParentContract = ethereumContractCreate(PARENT_CONTRACT);

        gasConsumedSelector = Objects.requireNonNull(mirrorClient
                .getContractInfo(deployedParentContract.contractId().toSolidityAddress())
                .getBytecode());
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
    @Then("the mirror node REST API should verify the ethereum called contract function")
    public void verifyDeployedContractMirror() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById();
        verifyContractExecutionResultsByTransactionId();
    }

    @Given("I successfully call function using EIP-1559 ethereum transaction")
    public void callContract() {
        ContractFunctionParameters parameters = new ContractFunctionParameters().addUint256(BigInteger.valueOf(1000));

        executeEthereumTransaction(deployedParentContract.contractId(), "createChild", parameters, EIP1559);

        gasConsumedSelector = encodeDataToByteArray(PARENT_CONTRACT, CREATE_CHILD, BigInteger.valueOf(1000));
    }

    @Given("I successfully call function using EIP-2930 ethereum transaction")
    public void getChildBytecode() {
        var executeContractResult =
                executeEthereumTransaction(deployedParentContract.contractId(), "getBytecode", null, EIP2930);

        childContractBytecodeFromParent =
                executeContractResult.contractFunctionResult().getBytes(0);
        assertThat(childContractBytecodeFromParent).isNotNull();

        gasConsumedSelector = encodeDataToByteArray(PARENT_CONTRACT, GET_BYTE_CODE);
    }

    @Then("the mirror node contract results API should return an accurate gas consumed")
    public void verifyGasConsumedIsCorrect() {
        String txId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        verifyGasConsumed(txId);
    }

    public DeployedContract ethereumContractCreate(ContractResource contractResource) {
        var resource = resourceLoader.getResource(contractResource.getPath());
        try (var in = resource.getInputStream()) {
            CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
            var fileContent = compiledSolidityArtifact.getBytecode().replaceFirst("0x", "");
            var fileId = persistContractBytes(fileContent);

            networkTransactionResponse = ethereumClient.createContract(
                    ethereumSignerPrivateKey, fileId, fileContent, contractResource.getInitialBalance());
            ContractId createdContractId = verifyCreateContractNetworkResponse();
            return new DeployedContract(fileId, createdContractId, compiledSolidityArtifact);
        } catch (IOException e) {
            log.warn("Issue creating contract: {}, ex: {}", contractResource, e);
            throw new RuntimeException(e);
        }
    }

    private ContractClient.ExecuteContractResult executeEthereumTransaction(
            ContractId contractId, String functionName, ContractFunctionParameters parameters, TransactionType type) {

        ContractClient.ExecuteContractResult executeContractResult =
                ethereumClient.executeContract(ethereumSignerPrivateKey, contractId, functionName, parameters, type);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertThat(networkTransactionResponse.getTransactionId()).isNotNull();
        assertThat(networkTransactionResponse.getReceipt()).isNotNull();
        assertThat(executeContractResult.contractFunctionResult()).isNotNull();

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

        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).isNotEmpty();
        var mirrorTransaction = finalizeHollowAccount ? transactions.get(1) : transactions.get(0);

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
