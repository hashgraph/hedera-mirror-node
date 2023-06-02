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

package com.hedera.mirror.test.e2e.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.config.Web3Properties;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorContractResult;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResultResponse;
import com.hedera.mirror.test.e2e.acceptance.util.FeatureInputHandler;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
    private static DeployedContract deployedParentContract;
    private final AccountClient accountClient;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private final AcceptanceTestProperties acceptanceTestProperties;
    private final Web3Properties web3Properties;
    private String create2ChildContractEvmAddress;
    private String create2ChildContractEntityId;
    private AccountId create2ChildContractAccountId;
    private ContractId create2ChildContractContractId;

    @Value("classpath:solidity/artifacts/contracts/Parent.sol/Parent.json")
    private Resource parentContract;

    private byte[] childContractBytecodeFromParent;

    @Given("I successfully create a contract from the parent contract bytes with {int} balance")
    public void createNewContract(int initialBalance) throws IOException {
        try (var in = parentContract.getInputStream()) {
            var parentCompiledSolidityArtifact = MAPPER.readValue(in, CompiledSolidityArtifact.class);
            deployedParentContract = createContract(parentCompiledSolidityArtifact, initialBalance);
        }
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
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId())
                .isEqualTo(deployedParentContract.contractId().toString());
    }

    @Then("the mirror node REST API should return status {int} for the self destruct transaction")
    public void verifyMirrorAPIContractChildSelfDestructResponses(int status) {
        var mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId()).isEqualTo(create2ChildContractEntityId);
    }

    @And("the mirror node REST API should return status {int} for the account transaction")
    public void verifyMirrorAPIAccountResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
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
        if (!web3Properties.isEnabled()) {
            return;
        }

        var from = contractClient.getClientAddress();
        var to = deployedParentContract.contractId().toSolidityAddress();

        var getAccountBalanceResponse = mirrorClient.contractsCall(GET_ACCOUNT_BALANCE_SELECTOR, to, from);
        assertThat(getAccountBalanceResponse.getResultAsNumber()).isEqualTo(1000L);

        var getSenderResponse = mirrorClient.contractsCall(GET_SENDER_SELECTOR, to, from);
        assertThat(getSenderResponse.getResultAsAddress()).isEqualTo(from);

        var multiplySimpleNumbersResponse = mirrorClient.contractsCall(MULTIPLY_SIMPLE_NUMBERS_SELECTOR, to, from);
        assertThat(multiplySimpleNumbersResponse.getResultAsNumber()).isEqualTo(4L);

        var identifierResponse = mirrorClient.contractsCall(IDENTIFIER_SELECTOR, to, from);
        assertThat(identifierResponse.getResultAsSelector()).isEqualTo(IDENTIFIER_SELECTOR);

        assertThatThrownBy(() -> mirrorClient.contractsCall(WRONG_SELECTOR, to, from))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("the mirror node REST API should verify the deleted contract entity")
    public void verifyDeletedContractMirror() {
        verifyContractFromMirror(true);
    }

    @Given("I call the parent contract to retrieve child contract bytecode")
    public void getChildContractBytecode() {
        var executeContractResult = executeGetChildContractBytecodeTransaction();
        childContractBytecodeFromParent =
                executeContractResult.contractFunctionResult().getBytes(0);
        assertNotNull(childContractBytecodeFromParent);
    }

    @When("I call the parent contract evm address function with the bytecode of the child contract")
    public void getCreate2ChildContractEvmAddress() {
        var executeContractResult = executeGetEvmAddressTransaction(EVM_ADDRESS_SALT);
        create2ChildContractEvmAddress =
                executeContractResult.contractFunctionResult().getAddress(0);
        create2ChildContractAccountId = AccountId.fromEvmAddress(create2ChildContractEvmAddress);
        create2ChildContractContractId = ContractId.fromEvmAddress(0, 0, create2ChildContractEvmAddress);
    }

    @And("I create a hollow account using CryptoTransfer of {int} to the evm address")
    public void createHollowAccountWithCryptoTransfertoEvmAddress(int amount) {
        networkTransactionResponse =
                accountClient.sendCryptoTransfer(create2ChildContractAccountId, Hbar.fromTinybars(amount));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @And("the mirror node REST API should verify the account receiving {int} is hollow")
    public void verifyMirrorAPIHollowAccountResponse(int amount) {
        var mirrorAccountResponse = mirrorClient.getAccountDetailsUsingEvmAddress(create2ChildContractAccountId);
        create2ChildContractEntityId = mirrorAccountResponse.getAccount();

        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(MirrorTransaction::getConsensusTimestamp))
                .toList();

        assertEquals(2, transactions.size());
        assertEquals("CRYPTOCREATEACCOUNT", transactions.get(0).getName());
        assertEquals("CRYPTOTRANSFER", transactions.get(1).getName());

        assertNotNull(mirrorAccountResponse.getAccount());
        assertEquals(amount, mirrorAccountResponse.getBalanceInfo().getBalance());
        // Hollow account indicated by not having a public key defined.
        assertEquals(ACCOUNT_EMPTY_KEYLIST, mirrorAccountResponse.getKey().getKey());
    }

    @And("the mirror node REST API should indicate not found when using evm address to retrieve as a contract")
    public void verifyMirrorAPIContractNotFoundResponse() {
        try {
            mirrorClient.getContractInfoWithNotFound(create2ChildContractEvmAddress);
            fail("Did not expect to find contract at EVM address");
        } catch (WebClientResponseException wcre) {
            assertEquals(HttpStatus.NOT_FOUND, wcre.getStatusCode());
        }
    }

    @When("I create a child contract by calling parent contract function to deploy using CREATE2")
    public void createChildContractUsingCreate2() {
        executeCreate2Transaction(EVM_ADDRESS_SALT);
    }

    @And("the mirror node REST API should retrieve the child contract when using evm address")
    public void verifyMirrorAPIContractFoundResponse() {
        var mirrorContractResponse = mirrorClient.getContractInfo(create2ChildContractEvmAddress);
        var transactions = mirrorClient
                .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                .getTransactions()
                .stream()
                .sorted(Comparator.comparing(MirrorTransaction::getConsensusTimestamp))
                .toList();

        assertNotNull(transactions);
        assertEquals(2, transactions.size());
        assertEquals(
                deployedParentContract.contractId.toString(),
                transactions.get(0).getEntityId());
        assertEquals("CONTRACTCALL", transactions.get(0).getName());
        assertEquals(create2ChildContractEntityId, transactions.get(1).getEntityId());
        assertEquals("CONTRACTCREATEINSTANCE", transactions.get(1).getName());

        String childContractBytecodeFromParentHex = HexFormat.of().formatHex(childContractBytecodeFromParent);
        assertEquals(
                childContractBytecodeFromParentHex,
                mirrorContractResponse.getBytecode().replaceFirst("0x", ""));
        assertEquals(
                create2ChildContractEvmAddress,
                mirrorContractResponse.getEvmAddress().replaceFirst("0x", ""));
    }

    @And("the mirror node REST API should verify the account is no longer hollow")
    public void verifyMirrorAPIFullAccountResponse() {
        var mirrorAccountResponse = mirrorClient.getAccountDetailsUsingEvmAddress(create2ChildContractAccountId);
        assertNotNull(mirrorAccountResponse.getAccount());
        assertNotEquals(ACCOUNT_EMPTY_KEYLIST, mirrorAccountResponse.getKey().getKey());
    }

    @When("I successfully delete the child contract by calling it and causing it to self destruct")
    public void deleteChildContractUsingSelfDestruct() {
        executeSelfDestructTransaction();
    }

    private ContractId verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
        return contractId;
    }

    private FileId persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[] {});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        return fileId;
    }

    private DeployedContract createContract(CompiledSolidityArtifact compiledSolidityArtifact, int initialBalance) {
        var fileId = persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                null);

        var contractId = verifyCreateContractNetworkResponse();
        return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
    }

    private MirrorContractResponse verifyContractFromMirror(boolean isDeleted) {
        var mirrorContract =
                mirrorClient.getContractInfo(deployedParentContract.contractId().toString());

        assertNotNull(mirrorContract);
        assertThat(mirrorContract.getAdminKey()).isNotNull();
        assertThat(mirrorContract.getAdminKey().getKey())
                .isEqualTo(contractClient
                        .getSdkClient()
                        .getExpandedOperatorAccountId()
                        .getPublicKey()
                        .toStringRaw());
        assertThat(mirrorContract.getAutoRenewPeriod()).isNotNull();
        assertThat(mirrorContract.getBytecode()).isNotBlank();
        assertThat(mirrorContract.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());
        assertThat(mirrorContract.getCreatedTimestamp()).isNotBlank();
        assertThat(mirrorContract.isDeleted()).isEqualTo(isDeleted);
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

    private void verifyContractExecutionResultsById() {
        List<MirrorContractResult> contractResults = mirrorClient
                .getContractResultsById(deployedParentContract.contractId().toString())
                .getResults();

        assertThat(contractResults).isNotEmpty().allSatisfy(this::verifyContractExecutionResults);
    }

    private void verifyContractExecutionResultsByTransactionId() {
        MirrorContractResultResponse contractResult = mirrorClient.getContractResultByTransactionId(
                networkTransactionResponse.getTransactionIdStringNoCheckSum());

        verifyContractExecutionResults(contractResult);
        assertThat(contractResult.getBlockHash()).isNotBlank();
        assertThat(contractResult.getBlockNumber()).isPositive();
        assertThat(contractResult.getHash()).isNotBlank();
    }

    private boolean isEmptyHex(String hexString) {
        return !StringUtils.hasLength(hexString) || hexString.equals("0x");
    }

    private void verifyContractExecutionResults(MirrorContractResult contractResult) {
        ContractExecutionStage contractExecutionStage = isEmptyHex(contractResult.getFunctionParameters())
                ? ContractExecutionStage.CREATION
                : ContractExecutionStage.CALL;

        assertThat(contractResult.getCallResult()).isNotBlank();
        assertThat(contractResult.getContractId())
                .isEqualTo(deployedParentContract.contractId().toString());
        String[] createdIds = contractResult.getCreatedContractIds();
        assertThat(createdIds).isNotEmpty();
        assertThat(contractResult.getErrorMessage()).isBlank();
        assertThat(contractResult.getFailedInitcode()).isBlank();
        assertThat(contractResult.getFrom())
                .isEqualTo(FeatureInputHandler.evmAddress(contractClient
                        .getSdkClient()
                        .getExpandedOperatorAccountId()
                        .getAccountId()));
        assertThat(contractResult.getGasLimit())
                .isEqualTo(contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas());
        assertThat(contractResult.getGasUsed()).isPositive();
        assertThat(contractResult.getTo())
                .isEqualTo(FeatureInputHandler.evmAddress(deployedParentContract.contractId()));

        int amount = 0; // no payment in contract construction phase
        int numCreatedIds = 2; // parent and child contract
        switch (contractExecutionStage) {
            case CREATION:
                amount = 10000000;
                assertThat(createdIds)
                        .contains(deployedParentContract.contractId().toString());
                assertThat(isEmptyHex(contractResult.getFunctionParameters())).isTrue();
                break;
            case CALL:
                numCreatedIds = 1;
                assertThat(createdIds)
                        .doesNotContain(deployedParentContract.contractId().toString());
                assertThat(isEmptyHex(contractResult.getFunctionParameters())).isFalse();
                break;
            default:
                break;
        }

        assertThat(contractResult.getAmount()).isEqualTo(amount);
        assertThat(createdIds).hasSize(numCreatedIds);
    }

    private void executeCreateChildTransaction(int transferAmount) {
        ContractFunctionParameters parameters =
                new ContractFunctionParameters().addUint256(BigInteger.valueOf(transferAmount));

        executeContractCallTransaction(deployedParentContract.contractId(), "createChild", parameters, null);
    }

    private ExecuteContractResult executeGetChildContractBytecodeTransaction() {
        return executeContractCallTransaction(deployedParentContract.contractId(), "getBytecode", null, null);
    }

    private ExecuteContractResult executeGetEvmAddressTransaction(int salt) {
        ContractFunctionParameters parameters = new ContractFunctionParameters()
                .addBytes(childContractBytecodeFromParent)
                .addUint256(BigInteger.valueOf(salt));

        return executeContractCallTransaction(deployedParentContract.contractId(), "getAddress", parameters, null);
    }

    private ExecuteContractResult executeCreate2Transaction(int salt) {
        ContractFunctionParameters parameters = new ContractFunctionParameters()
                .addBytes(childContractBytecodeFromParent)
                .addUint256(BigInteger.valueOf(salt));

        return executeContractCallTransaction(deployedParentContract.contractId(), "create2Deploy", parameters, null);
    }

    // This is a function call on the CREATE2 created child contract, not the parent.
    private ExecuteContractResult executeSelfDestructTransaction() {
        return executeContractCallTransaction(create2ChildContractContractId, "vacateAddress", null, null);
    }

    private ExecuteContractResult executeContractCallTransaction(
            ContractId contractId, String functionName, ContractFunctionParameters parameters, Hbar payableAmount) {

        ExecuteContractResult executeContractResult = contractClient.executeContract(
                contractId,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                functionName,
                parameters,
                payableAmount);

        networkTransactionResponse = executeContractResult.networkTransactionResponse();
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertNotNull(executeContractResult.contractFunctionResult());

        return executeContractResult;
    }

    private enum ContractExecutionStage {
        CREATION,
        CALL
    }

    /*
     * Static state to persist across contract Cucumber scenarios.
     */
    private record DeployedContract(
            FileId fileId, ContractId contractId, CompiledSolidityArtifact compiledSolidityArtifact) {}
}
