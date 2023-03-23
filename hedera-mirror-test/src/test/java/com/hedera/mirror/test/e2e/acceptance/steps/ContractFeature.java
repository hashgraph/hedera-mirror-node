package com.hedera.mirror.test.e2e.acceptance.steps;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse.convertContractCallResponseToAddress;
import static com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse.convertContractCallResponseToNum;
import static com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse.convertContractCallResponseToSelector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;

import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorContractResult;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResultResponse;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;

@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ContractFeature extends AbstractFeature {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private static final String GET_ACCOUNT_BALANCE_SELECTOR = "6896fabf";
    private static final String GET_SENDER_SELECTOR = "5e01eb5a";
    private static final String MULTIPLY_SIMPLE_NUMBERS_SELECTOR = "8070450f";
    private static final String IDENTIFIER_SELECTOR = "7998a1c4";
    private static final String WRONG_SELECTOR = "000000";
    private static final String ACCOUNT_EMPTY_KEYLIST = "3200";
    private static final int EVM_ADDRESS_SALT = 42;

    /*
     * Static state to persist across Cucumber scenarios.
     */
    record DeployedContract(FileId fileId, ContractId contractId, CompiledSolidityArtifact compiledSolidityArtifact) {};
    private static DeployedContract deployedParentContract;

    private String create2ChildContractEvmAddress;
    private AccountId create2ChildContractAccountId;
    private ContractId create2ChildContractContractId;

    private final AccountClient accountClient;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private final AcceptanceTestProperties acceptanceTestProperties;

    @Value("classpath:solidity/artifacts/contracts/Parent.sol/Parent.json")
    private Path parentContract;
    private byte[] childContractBytecodeFromParent;

    @Given("I successfully create a contract from the parent contract bytes with {int} balance")
    public void createNewContract(int initialBalance) throws IOException {
        CompiledSolidityArtifact parentCompiledSolidityArtifact = MAPPER.readValue(
                ResourceUtils.getFile(parentContract.toUri()),
                CompiledSolidityArtifact.class);
        deployedParentContract = createContract(parentCompiledSolidityArtifact, initialBalance);
    }

    @Given("I successfully call the contract")
    public void callContract() {
        // log and results to be verified
        executeCreateChildTransaction(1000);
    }

    @Given("I successfully update the contract")
    public void updateContract() {
        networkTransactionResponse = contractClient.updateContract(deployedParentContract.contractId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully delete the parent contract")
    public void deleteParentContract() {
        networkTransactionResponse = contractClient.deleteContract(
                deployedParentContract.contractId(),
                contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId(),
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully delete the parent contract bytecode file")
    public void deleteParentContractFile() {
        networkTransactionResponse = fileClient.deleteFile(deployedParentContract.fileId());

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should return status {int} for the contract transaction")
    public void verifyMirrorAPIContractResponses(int status) {
        log.info("Verify contract transaction");
        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId()).isEqualTo(deployedParentContract.contractId().toString());
    }

    @And("the mirror node REST API should return status {int} for the account transaction")
    public void verifyMirrorAPIAccountResponses(int status) {
        log.info("Verify account transaction");
        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
//        assertThat(mirrorTransaction.getEntityId()).isEqualTo(childContractEvmAddressAccountId);
    }

    @Then("the mirror node REST API should verify the deployed contract entity")
    public void verifyDeployedContractMirror() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById();
        verifyContractExecutionResultsByTransactionId();
    }

    @Then("the mirror node REST API should verify the updated contract entity")
    public void verifyUpdatedContractMirror() {
        verifyContractFromMirror(false);
    }

    @Then("the mirror node REST API should verify the called contract function")
    public void verifyContractFunctionCallMirror() {
        verifyContractFromMirror(false);
        verifyContractExecutionResultsById();
        verifyContractExecutionResultsByTransactionId();
    }

    @Then("I call the contract via the mirror node REST API")
    public void restContractCall() {
        if(!acceptanceTestProperties.isContractTraceability()) {
            return;
        }

        var from = contractClient.getClientAddress();
        var to = deployedParentContract.contractId().toSolidityAddress();

        var getAccountBalanceResponse = mirrorClient.contractsCall(GET_ACCOUNT_BALANCE_SELECTOR, to, from);
        assertThat(convertContractCallResponseToNum(getAccountBalanceResponse)).isEqualTo(BigInteger.valueOf(1000L));

        var getSenderResponse = mirrorClient.contractsCall(GET_SENDER_SELECTOR, to, from);
        assertThat(convertContractCallResponseToAddress(getSenderResponse)).isEqualTo(from);

        var multiplySimpleNumbersResponse = mirrorClient.contractsCall(MULTIPLY_SIMPLE_NUMBERS_SELECTOR, to, from);
        assertThat(convertContractCallResponseToNum(multiplySimpleNumbersResponse)).isEqualTo(BigInteger.valueOf(4L));

        var identifierResponse = mirrorClient.contractsCall(IDENTIFIER_SELECTOR, to, from);
        assertThat(convertContractCallResponseToSelector(identifierResponse)).isEqualTo(IDENTIFIER_SELECTOR);

        assertThatThrownBy(() -> mirrorClient.contractsCall(WRONG_SELECTOR, to, from))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("the mirror node REST API should verify the deleted contract entity")
    public void verifyDeletedContractMirror() {
        verifyContractFromMirror(true);
    }

    @Then("I call the parent contract to retrieve child contract bytecode")
    public void getChildContractBytecode() {

        ExecuteContractResult executeContractResult = executeGetChildContractBytecodeTransaction();
        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        childContractBytecodeFromParent = executeContractResult.contractFunctionResult().getBytes(0);
        assertNotNull(childContractBytecodeFromParent);
    }

    @Then("I call the parent contract evm address function with the bytecode of the child contract")
    public void getCreate2ChildContractEvmAddress() throws IOException {
        ExecuteContractResult executeContractResult = executeGetEvmAddressTransaction(EVM_ADDRESS_SALT);
        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        create2ChildContractEvmAddress = executeContractResult.contractFunctionResult().getAddress(0);
        create2ChildContractAccountId = AccountId.fromString(String.format("0.0.%s", create2ChildContractEvmAddress));
        create2ChildContractContractId = ContractId.fromString(create2ChildContractAccountId.toString());
    }

    @Then("I create a hollow account using CryptoTransfer of {int} to the evm address")
    public void createHollowAccountWithCryptoTransfertoEvmAddress(int amount) {
        networkTransactionResponse = accountClient.sendCryptoTransfer(create2ChildContractAccountId, Hbar.fromTinybars(amount));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should verify the account receiving {int} is hollow")
    public void verifyMirrorAPIHollowAccountResponse(int amount) {
        log.info("Verify cryptotransfer to evm address account is hollow");
        var mirrorAccountResponse = mirrorClient.getAccountDetailsUsingEvmAddress(
                create2ChildContractAccountId);
        var transactions = mirrorClient.getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum()).getTransactions();
        assertNotNull(transactions);
        assertEquals(2, transactions.size());   // create and transfer

        assertNotNull(mirrorAccountResponse.getAccount());
        assertEquals(amount, mirrorAccountResponse.getBalanceInfo().getBalance());
        // Hollow account indicated by not having a public key defined.
        assertEquals(ACCOUNT_EMPTY_KEYLIST, mirrorAccountResponse.getKey().getKey());
    }

    @And("the mirror node REST API should indicate not found when using evm address to retrieve as a contract")
    public void verifyMirrorAPIContractNotFoundResponse() {
        log.info("Verify contract at the hollow account evm address does not exist");
        try {
            MirrorContractResponse mirrorContractResponse = mirrorClient.getContractInfoWithNotFound(
                    create2ChildContractEvmAddress);
            log.error("Expected contract at EVM address {} to not exist, but found: {}", create2ChildContractEvmAddress, mirrorContractResponse);
            fail();
        } catch (WebClientResponseException wcre) {
            assertEquals(HttpStatus.NOT_FOUND, wcre.getStatusCode());
        }
    }

    @Then("I deploy a child contract by calling parent contract function to deploy to the evm address using CREATE2")
    public void createChildContractUsingCreate2() {
        ExecuteContractResult executeContractResult = executeCreate2Transaction(EVM_ADDRESS_SALT);
        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        ContractId contractId = verifyCreateContractNetworkResponse();
        assertNotNull(contractId); // Remove - done already.
    }

    @Then("the mirror node REST API should retrieve the child contract when using evm address")
    public void verifyMirrorAPIContractFoundResponse() {
        log.info("Verify contract at the now full account evm address does now exist");
        MirrorContractResponse mirrorContractResponse = mirrorClient.getContractInfo(create2ChildContractEvmAddress);
//        assertEquals(childContractEvmAddressContractId, mirrorContractResponse.getContractId());
    }

    @And("the mirror node REST API should verify the account is no longer hollow")
    public void verifyMirrorAPIFullAccountResponse() {
        log.info("Verify cryptotransfer to evm address account is no full");
        var mirrorAccountResponse = mirrorClient.getAccountDetailsUsingEvmAddress(
                create2ChildContractAccountId);
        var transactions = mirrorClient.getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum()).getTransactions();
        assertNotNull(transactions);

        assertNotNull(mirrorAccountResponse.getAccount());
        // Hollow account indicated by not having a public key defined.
        assertNotEquals(ACCOUNT_EMPTY_KEYLIST, mirrorAccountResponse.getKey().getKey());
    }

    @Then("I successfully delete the child contract by calling it and causing it to self destruct")
    public void deleteChildContractUsingSelfDestruct() {
        ExecuteContractResult executeContractResult = executeSelftDestructTransaction();
        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private ContractId verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        ContractId contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
        return contractId;
    }

    private FileId persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[] {});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        FileId fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);
        log.info("Created file {} to hold contract init code", fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        return fileId;
    }

    private DeployedContract createContract(CompiledSolidityArtifact compiledSolidityArtifact, int initialBalance) {
        FileId fileId = persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                null);

        ContractId contractId = verifyCreateContractNetworkResponse();
        return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
    }

    private MirrorContractResponse verifyContractFromMirror(boolean isDeleted) {
        MirrorContractResponse mirrorContract = mirrorClient.getContractInfo(deployedParentContract.contractId().toString());

        assertNotNull(mirrorContract);
        assertThat(mirrorContract.getAdminKey()).isNotNull();
        assertThat(mirrorContract.getAdminKey().getKey())
                .isEqualTo(contractClient.getSdkClient().getExpandedOperatorAccountId().getPublicKey().toStringRaw());
        assertThat(mirrorContract.getAutoRenewPeriod()).isNotNull();
        assertThat(mirrorContract.getBytecode()).isNotBlank();
        assertThat(mirrorContract.getContractId()).isEqualTo(deployedParentContract.contractId().toString());
        assertThat(mirrorContract.getCreatedTimestamp()).isNotBlank();
        assertThat(mirrorContract.isDeleted()).isEqualTo(isDeleted);
        assertThat(mirrorContract.getFileId()).isEqualTo(deployedParentContract.fileId().toString());
        assertThat(mirrorContract.getMemo()).isNotBlank();
        String address = mirrorContract.getEvmAddress();
        assertThat(address).isNotBlank().isNotEqualTo("0x").isNotEqualTo("0x0000000000000000000000000000000000000000");
        assertThat(mirrorContract.getTimestamp()).isNotNull();
        assertThat(mirrorContract.getTimestamp().getFrom()).isNotNull();

        if (contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties().isSidecars()) {
            assertThat(mirrorContract.getRuntimeBytecode()).isNotNull();
        }

        assertThat(mirrorContract.getBytecode()).isEqualTo(deployedParentContract.compiledSolidityArtifact().getBytecode());

        if (isDeleted) {
            assertThat(mirrorContract.getObtainerId())
                    .isEqualTo(contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId().toString());
        } else {
            assertThat(mirrorContract.getObtainerId()).isNull();
        }

        return mirrorContract;
    }

    private void verifyContractExecutionResultsById() {
        List<MirrorContractResult> contractResults = mirrorClient.getContractResultsById(deployedParentContract.contractId().toString())
                .getResults();

        assertThat(contractResults).isNotEmpty().allSatisfy(this::verifyContractExecutionResults);
    }

    private void verifyContractExecutionResultsByTransactionId() {
        MirrorContractResultResponse contractResult = mirrorClient
                .getContractResultByTransactionId(networkTransactionResponse.getTransactionIdStringNoCheckSum());

        verifyContractExecutionResults(contractResult);
        assertThat(contractResult.getBlockHash()).isNotBlank();
        assertThat(contractResult.getBlockNumber()).isPositive();
        assertThat(contractResult.getHash()).isNotBlank();
    }

    private boolean isEmptyHex(String hexString) {
        return StringUtils.isEmpty(hexString) || hexString.equals("0x");
    }

    private void verifyContractExecutionResults(MirrorContractResult contractResult) {
        ContractExecutionStage contractExecutionStage = isEmptyHex(contractResult.getFunctionParameters()) ?
                ContractExecutionStage.CREATION : ContractExecutionStage.CALL;

        assertThat(contractResult.getCallResult()).isNotBlank();
        assertThat(contractResult.getContractId()).isEqualTo(deployedParentContract.contractId().toString());
        String[] createdIds = contractResult.getCreatedContractIds();
        assertThat(createdIds).isNotEmpty();
        assertThat(contractResult.getErrorMessage()).isBlank();
        assertThat(contractResult.getFailedInitcode()).isBlank();
        assertThat(contractResult.getFrom()).isEqualTo(FeatureInputHandler.evmAddress(
                contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId()));
        assertThat(contractResult.getGasLimit())
                .isEqualTo(contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas());
        assertThat(contractResult.getGasUsed()).isPositive();
        assertThat(contractResult.getTo()).isEqualTo(FeatureInputHandler.evmAddress(deployedParentContract.contractId()));

        int amount = 0; // no payment in contract construction phase
        int numCreatedIds = 2; // parent and child contract
        switch (contractExecutionStage) {
            case CREATION:
                amount = 10000000;
                assertThat(createdIds).contains(deployedParentContract.contractId().toString());
                assertThat(isEmptyHex(contractResult.getFunctionParameters())).isTrue();
                break;
            case CALL:
                numCreatedIds = 1;
                assertThat(createdIds).doesNotContain(deployedParentContract.contractId().toString());
                assertThat(isEmptyHex(contractResult.getFunctionParameters())).isFalse();
                break;
            default:
                break;
        }

        assertThat(contractResult.getAmount()).isEqualTo(amount);
        assertThat(createdIds).hasSize(numCreatedIds);
    }

    private void executeCreateChildTransaction(int transferAmount) {
        ExecuteContractResult executeContractResult = contractClient.executeContract(
                deployedParentContract.contractId(),
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                "createChild",
                new ContractFunctionParameters()
                        .addUint256(BigInteger.valueOf(transferAmount)),
                null);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        ContractFunctionResult contractFunctionResult = executeContractResult.contractFunctionResult();
        assertNotNull(contractFunctionResult);

    }

    private ExecuteContractResult executeGetChildContractBytecodeTransaction() {
        ExecuteContractResult executeContractResult = contractClient.executeContract(
                deployedParentContract.contractId(),
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                "getBytecode",
                null,
                null);

        NetworkTransactionResponse networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        return executeContractResult;
    }

    private ExecuteContractResult executeGetEvmAddressTransaction(int salt) {
        ExecuteContractResult executeContractResult = contractClient.executeContract(
                deployedParentContract.contractId(),
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                "getAddress",
                new ContractFunctionParameters()
                        .addBytes(childContractBytecodeFromParent)
                        .addUint256(BigInteger.valueOf(salt)),
                null);

        NetworkTransactionResponse networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        return executeContractResult;
    }

    private ExecuteContractResult executeCreate2Transaction(int salt) {
        ExecuteContractResult executeContractResult = contractClient.executeContract(
                deployedParentContract.contractId(),
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                "create2Deploy",
                new ContractFunctionParameters()
                        .addBytes(childContractBytecodeFromParent)
                        .addUint256(BigInteger.valueOf(salt)),
                null);

        NetworkTransactionResponse networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        return executeContractResult;
    }

    // This is a function call on the newly created child contract, not the parent.
    private ExecuteContractResult executeSelftDestructTransaction() {
        ExecuteContractResult executeContractResult = contractClient.executeContract(
                create2ChildContractContractId,
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                "vacateAddress",
               null,
                null);

        NetworkTransactionResponse networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        return executeContractResult;
    }

    private enum ContractExecutionStage {
        CREATION,
        CALL
    }
}
