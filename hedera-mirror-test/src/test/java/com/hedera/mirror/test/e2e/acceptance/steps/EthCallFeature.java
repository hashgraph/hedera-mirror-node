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
import static com.hedera.mirror.rest.model.TransactionTypes.CONTRACTCREATEINSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hashgraph.sdk.*;
import com.hedera.mirror.rest.model.ContractResponse;
import com.hedera.mirror.rest.model.ContractResult;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import com.hedera.mirror.rest.model.TransactionDetail;
import com.hedera.mirror.test.e2e.acceptance.client.EthereumClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;
import com.hedera.mirror.test.e2e.acceptance.util.ethereum.EthTxData;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.CustomLog;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@CustomLog
public class EthCallFeature extends AbstractFeature {

    private static final int EVM_ADDRESS_SALT = 42;
    private static final String ACCOUNT_EMPTY_KEYLIST = "3200";
    protected AccountId ethereum_signer_account;
    protected PrivateKey ethereum_signer_private_key;
    private String account;
    private DeployedContract deployedParentContract;
    private byte[] childContractBytecodeFromParent;
    private String create2ChildContractEvmAddress;
    private String create2ChildContractEntityId;
    private AccountId create2ChildContractAccountId;
    private ContractId create2ChildContractContractId;

    @Given("I successfully created a signer account with an EVM address alias")
    public void createAccountWithEvmAddressAlias() {
        ethereum_signer_private_key = PrivateKey.generateECDSA();
        ethereum_signer_account = ethereum_signer_private_key.getPublicKey().toAccountId(0, 0);

        networkTransactionResponse = accountClient.sendCryptoTransfer(ethereum_signer_account, Hbar.from(500L), null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("validate the signer account and its balance")
    public void verifyAccountCreated() {
        var accountInfo = mirrorClient.getAccountDetailsUsingAlias(ethereum_signer_account);
        account = accountInfo.getAccount();
        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(TransactionDetail::getConsensusTimestamp))
                .toList();

        assertThat(accountInfo.getAccount()).isNotNull();
        assertThat(accountInfo.getBalance().getBalance())
                .isEqualTo(Hbar.from(500L).toTinybars());

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

    @Given("I successfully create parent contract by ethereum transaction")
    public void createNewERCtestContract() {
        deployedParentContract = ethereumContractCreate(ContractResource.PARENT_CONTRACT);
        deployedParentContract.contractId().toSolidityAddress();
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
        verifyContractExecutionResultsById(true);
        verifyContractExecutionResultsByTransactionId(true);
    }

    @Given("I successfully call the child creation function using EIP-1559 ethereum transaction")
    public void callContract() {
        ContractFunctionParameters parameters = new ContractFunctionParameters().addUint256(BigInteger.valueOf(1000));

        executeEthereumTransaction(
                deployedParentContract.contractId(),
                "createChild",
                parameters,
                null,
                EthTxData.EthTransactionType.EIP1559);
    }

    @Then("the mirror node REST API should verify the child creation ethereum transaction")
    public void verifyChildCreationEthereumCall() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById(true);
        verifyContractExecutionResultsByTransactionId(true);
    }

    @Then("the mirror node REST API should verify the ethereum called contract function")
    public void verifyContractFunctionCallMirror() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById(false);
        verifyContractExecutionResultsByTransactionId(false);
    }

    @Given("I call the parent contract to retrieve child's bytecode by Legacy ethereum transaction")
    public void getChildBytecode() {
        var executeContractResult = executeEthereumTransaction(
                deployedParentContract.contractId(),
                "getBytecode",
                null,
                null,
                EthTxData.EthTransactionType.LEGACY_ETHEREUM);

        childContractBytecodeFromParent =
                executeContractResult.contractFunctionResult().getBytes(0);
        assertNotNull(childContractBytecodeFromParent);
    }

    @When(
            "I call the parent contract evm address function with the bytecode of the child with EIP-2930 ethereum transaction")
    public void getChildAddress() {
        ContractFunctionParameters parameters = new ContractFunctionParameters()
                .addBytes(childContractBytecodeFromParent)
                .addUint256(BigInteger.valueOf(EVM_ADDRESS_SALT));

        var executeContractResult = executeEthereumTransaction(
                deployedParentContract.contractId(),
                "getAddress",
                parameters,
                null,
                EthTxData.EthTransactionType.EIP2930);

        create2ChildContractEvmAddress =
                executeContractResult.contractFunctionResult().getAddress(0);
        create2ChildContractAccountId = AccountId.fromEvmAddress(create2ChildContractEvmAddress);
        create2ChildContractContractId = ContractId.fromEvmAddress(0, 0, create2ChildContractEvmAddress);
    }

    @And("I create a hollow account using CryptoTransfer of {int} to the child's evm address")
    public void createHollowAccountWithCryptoTransfertoEvmAddress(int amount) {
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(create2ChildContractAccountId, Hbar.fromTinybars(amount), null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @And("the mirror node REST API should verify the account is hollow and has {int}")
    public void verifyMirrorAPIHollowAccountResponse(int amount) {
        var mirrorAccountResponse = mirrorClient.getAccountDetailsUsingEvmAddress(create2ChildContractAccountId);
        create2ChildContractEntityId = mirrorAccountResponse.getAccount();

        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(TransactionDetail::getConsensusTimestamp))
                .toList();

        assertEquals(2, transactions.size());
        assertEquals(CRYPTOCREATEACCOUNT, transactions.get(0).getName());
        assertEquals(CRYPTOTRANSFER, transactions.get(1).getName());

        assertNotNull(mirrorAccountResponse.getAccount());
        assertEquals(amount, mirrorAccountResponse.getBalance().getBalance());
        // Hollow account indicated by not having a public key defined.
        assertEquals(ACCOUNT_EMPTY_KEYLIST, mirrorAccountResponse.getKey().getKey());
    }

    @And("the mirror node REST API should not find a contract when using child's evm address")
    public void verifyMirrorAPIContractNotFoundResponse() {
        try {
            mirrorClient.getContractInfo(create2ChildContractEvmAddress);
            fail("Did not expect to find contract at EVM address");
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @When("I create a child contract by calling the parent contract function to deploy using CREATE2 with EIP-1559")
    public void createChildContractUsingCreate2() {
        ContractFunctionParameters parameters = new ContractFunctionParameters()
                .addBytes(childContractBytecodeFromParent)
                .addUint256(BigInteger.valueOf(EVM_ADDRESS_SALT));
        executeEthereumTransaction(
                deployedParentContract.contractId(),
                "create2Deploy",
                parameters,
                null,
                EthTxData.EthTransactionType.EIP1559);
    }

    @And("the mirror node REST API should retrieve the contract when using child's evm address")
    public void verifyMirrorAPIContractFoundResponse() {
        var mirrorContractResponse = mirrorClient.getContractInfo(create2ChildContractEvmAddress);
        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(TransactionDetail::getConsensusTimestamp))
                .toList();

        assertNotNull(transactions);
        assertEquals(2, transactions.size());
        assertEquals(
                deployedParentContract.contractId().toString(),
                transactions.get(0).getEntityId());
        assertEquals(ETHEREUMTRANSACTION, transactions.get(0).getName());
        assertEquals(create2ChildContractEntityId, transactions.get(1).getEntityId());
        assertEquals(CONTRACTCREATEINSTANCE, transactions.get(1).getName());

        String childContractBytecodeFromParentHex = HexFormat.of().formatHex(childContractBytecodeFromParent);
        assertEquals(
                childContractBytecodeFromParentHex,
                mirrorContractResponse.getBytecode().replaceFirst("0x", ""));
        assertEquals(
                create2ChildContractEvmAddress,
                mirrorContractResponse.getEvmAddress().replaceFirst("0x", ""));
    }

    @And("the mirror node REST API should verify that the account is not hollow")
    public void verifyMirrorAPIFullAccountResponse() {
        var mirrorAccountResponse = mirrorClient.getAccountDetailsUsingEvmAddress(create2ChildContractAccountId);
        assertNotNull(mirrorAccountResponse.getAccount());
        assertNotEquals(ACCOUNT_EMPTY_KEYLIST, mirrorAccountResponse.getKey().getKey());
    }

    @When(
            "I successfully delete the child contract by calling vacateAddress function using EIP-1559 ethereum transaction")
    public void deleteChildContractUsingSelfDestruct() {
        executeEthereumTransaction(
                create2ChildContractContractId, "vacateAddress", null, null, EthTxData.EthTransactionType.EIP1559);
    }

    @Then("the mirror node REST API should return status {int} for the vacateAddress call transaction")
    public void verifyMirrorAPIContractChildSelfDestructResponses(int status) {
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId()).isEqualTo(create2ChildContractEntityId);
    }

    @Given("I successfully delete the parent bytecode file")
    public void deleteParentContractFile() {
        networkTransactionResponse = fileClient.deleteFile(deployedParentContract.fileId());

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @And("the mirror node REST API should return status {int} for the transfer transaction")
    public void verifyMirrorAPIAccountResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    public DeployedContract ethereumContractCreate(ContractResource contractResource) {
        var resource = resourceLoader.getResource(contractResource.getPath());
        try (var in = resource.getInputStream()) {
            CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
            var fileContent = compiledSolidityArtifact.getBytecode().replaceFirst("0x", "");
            var fileId = persistContractBytes(fileContent);

            networkTransactionResponse = ethereumClient.createContract(
                    ethereum_signer_private_key,
                    fileId,
                    fileContent,
                    ethereumClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    contractResource.getInitialBalance() == 0
                            ? null
                            : Hbar.fromTinybars(contractResource.getInitialBalance()),
                    null);
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
                ethereum_signer_private_key,
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

    private ContractResponse verifyContractFromMirror(boolean isDeleted) {
        var mirrorContract =
                mirrorClient.getContractInfo(deployedParentContract.contractId().toString());

        assertNotNull(mirrorContract);
        assertThat(mirrorContract.getAutoRenewPeriod()).isNotNull();
        assertThat(mirrorContract.getBytecode()).isNotBlank();
        assertThat(mirrorContract.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());
        assertThat(mirrorContract.getCreatedTimestamp()).isNotBlank();
        assertThat(mirrorContract.getDeleted()).isEqualTo(isDeleted);
        assertThat(mirrorContract.getFileId())
                .isEqualTo(deployedParentContract.fileId().toString());
        assertThat(mirrorContract.getMemo()).isNotBlank();
        String address = mirrorContract.getEvmAddress();
        assertThat(address).isNotBlank().isNotEqualTo("0x").isNotEqualTo("0x0000000000000000000000000000000000000000");
        assertThat(mirrorContract.getTimestamp()).isNotNull();
        assertThat(mirrorContract.getTimestamp().getFrom()).isNotNull();

        if (contractClient
                .getSdkClient()
                .getAcceptanceTestProperties()
                .getFeatureProperties()
                .isSidecars()) {
            assertThat(mirrorContract.getRuntimeBytecode()).isNotNull();
        }

        assertThat(mirrorContract.getBytecode())
                .isEqualTo(deployedParentContract.compiledSolidityArtifact().getBytecode());

        if (isDeleted) {
            assertThat(mirrorContract.getObtainerId())
                    .isEqualTo(contractClient
                            .getSdkClient()
                            .getExpandedOperatorAccountId()
                            .getAccountId()
                            .toString());
        } else {
            assertThat(mirrorContract.getObtainerId()).isNull();
        }

        return mirrorContract;
    }

    private void verifyContractExecutionResultsById(boolean isCreation) {
        List<ContractResult> contractResults = mirrorClient
                .getContractResultsById(deployedParentContract.contractId().toString())
                .getResults();

        assertThat(contractResults)
                .isNotEmpty()
                .allSatisfy(result -> verifyEthereumContractExecutionResults(result, isCreation));
    }

    private void verifyContractExecutionResultsByTransactionId(boolean isCreation) {
        ContractResult contractResult = mirrorClient.getContractResultByTransactionId(
                networkTransactionResponse.getTransactionIdStringNoCheckSum());

        verifyEthereumContractExecutionResults(contractResult, isCreation);
        assertThat(contractResult.getBlockHash()).isNotBlank();
        assertThat(contractResult.getBlockNumber()).isPositive();
        assertThat(contractResult.getHash()).isNotBlank();
    }

    private void verifyEthereumContractExecutionResults(ContractResult contractResult, boolean isCreation) {
        assertThat(contractResult.getCallResult()).isNotBlank();
        assertThat(contractResult.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());
        var createdIds = contractResult.getCreatedContractIds();
        if (isCreation) {
            assertThat(createdIds).isNotEmpty();
        }
        assertThat(contractResult.getErrorMessage()).isBlank();
        assertThat(contractResult.getFailedInitcode()).isBlank();
        assertThat(contractResult.getFrom()).isEqualTo(FeatureInputHandler.evmAddress(AccountId.fromString(account)));
        assertThat(contractResult.getGasLimit())
                .isEqualTo(contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas());
        assertThat(contractResult.getGasUsed()).isPositive();
        assertThat(contractResult.getTo())
                .isEqualTo(FeatureInputHandler.evmAddress(deployedParentContract.contractId()));
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
