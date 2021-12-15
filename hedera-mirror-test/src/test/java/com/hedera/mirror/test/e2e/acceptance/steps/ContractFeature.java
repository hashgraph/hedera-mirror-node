package com.hedera.mirror.test.e2e.acceptance.steps;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.junit.platform.engine.Cucumber;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ResourceUtils;

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
@Cucumber
public class ContractFeature extends AbstractFeature {
    private final static long maxFunctionGas = 300_000;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private ContractId contractId;
    private FileId fileId;
    private CompiledSolidityArtifact compiledSolidityArtifact;

    @Value("classpath:solidity/artifacts/contracts/Parent.sol/Parent.json")
    private Path parentContract;

    @Autowired
    private ContractClient contractClient;

    @Autowired
    private FileClient fileClient;

    @Autowired
    private MirrorNodeClient mirrorClient;

    @Given("I successfully create a contract from contract bytes with {int} balance")
    public void createNewContract(int initialBalance) throws IOException {
        compiledSolidityArtifact = mapper.readValue(
                ResourceUtils.getFile(parentContract.toUri()),
                CompiledSolidityArtifact.class);
        createContract(compiledSolidityArtifact.getBytecode(), initialBalance);
    }

    @Given("I successfully call the contract")
    public void callContract() {
        // log and results to be verified
        executeCreateChildTransaction(1000);
    }

    @Given("I successfully update the contract")
    public void updateContract() {
        networkTransactionResponse = contractClient.updateContract(contractId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully delete the contract")
    public void deleteContract() {
        networkTransactionResponse = contractClient.deleteContract(
                contractId,
                contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId(),
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should return status {int} for the contract transaction")
    public void verifyMirrorAPIResponses(int status) {
        log.info("Verify contract transaction");
        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorClient, status);
        assertThat(mirrorTransaction.getEntityId()).isEqualTo(contractId.toString());
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

    @Then("the mirror node REST API should verify the deleted contract entity")
    public void verifyDeletedContractMirror() {
        verifyContractFromMirror(true);
    }

    private void verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
    }

    private void persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[] {});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);
        log.info("Created file {} to hold contract init code", fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void createContract(String byteCode, int initialBalance) {
        persistContractBytes(byteCode.replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                maxFunctionGas,
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                null);

        verifyCreateContractNetworkResponse();
    }

    private MirrorContractResponse verifyContractFromMirror(boolean isDeleted) {
        MirrorContractResponse mirrorContract = mirrorClient.getContractInfo(contractId.toString());

        assertNotNull(mirrorContract);
        assertThat(mirrorContract.getAdminKey()).isNotNull();
        assertThat(mirrorContract.getAdminKey().getKey())
                .isEqualTo(contractClient.getSdkClient().getExpandedOperatorAccountId().getPublicKey().toStringRaw());
        assertThat(mirrorContract.getAutoRenewPeriod()).isNotNull();
        assertThat(mirrorContract.getBytecode()).isNotBlank();
        assertThat(mirrorContract.getContractId()).isEqualTo(contractId.toString());
        assertThat(mirrorContract.getCreatedTimestamp()).isNotBlank();
        assertThat(mirrorContract.isDeleted()).isEqualTo(isDeleted);
        assertThat(mirrorContract.getFileId()).isEqualTo(fileId.toString());
        assertThat(mirrorContract.getMemo()).isNotBlank();
        String address = mirrorContract.getSolidityAddress();
        assertThat(address).isNotBlank().isNotEqualTo("0x").isNotEqualTo("0x0000000000000000000000000000000000000000");
        assertThat(mirrorContract.getTimestamp()).isNotNull();
        assertThat(mirrorContract.getTimestamp().getFrom()).isNotNull();

        // contract bytes string goes through a series of transformations
        // remove '0x' prior to conversion to byte[], then encode and add 0x back for REST API
        String stringMinusOx = compiledSolidityArtifact.getBytecode().replaceFirst("0x", "");
        assertThat(mirrorContract.getBytecode())
                .isEqualTo("0x" + Hex.encodeHexString(stringMinusOx.getBytes(StandardCharsets.UTF_8)));

        if (isDeleted) {
            assertThat(mirrorContract.getObtainerId())
                    .isEqualTo(contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId().toString());
        } else {
            assertThat(mirrorContract.getObtainerId()).isNull();
        }

        return mirrorContract;
    }

    private void verifyContractExecutionResultsById() {
        List<MirrorContractResult> contractResults = mirrorClient.getContractResultsById(contractId.toString())
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

    private void verifyContractExecutionResults(MirrorContractResult contractResult) {
        ContractExecutionStage contractExecutionStage = contractResult.getFunctionParameters().equals("0x") ?
                ContractExecutionStage.CREATION : ContractExecutionStage.CALL;

        assertThat(contractResult.getCallResult()).isNotBlank();
        assertThat(contractResult.getContractId()).isEqualTo(contractId.toString());
        String[] createdIds = contractResult.getCreatedContractIds();
        assertThat(createdIds).isNotEmpty();
        assertThat(contractResult.getErrorMessage()).isBlank();
        assertThat(contractResult.getFrom()).isEqualTo(FeatureInputHandler.solidityAddress(
                contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId()));
        assertThat(contractResult.getGasLimit()).isEqualTo(maxFunctionGas);
        assertThat(contractResult.getGasUsed()).isPositive();
        assertThat(contractResult.getTo()).isEqualTo(FeatureInputHandler.solidityAddress(contractId));

        int amount = 0; // no payment in contract construction phase
        int numCreatedIds = 2; // parent and child contract
        switch (contractExecutionStage) {
            case CREATION:
                amount = 1000;
                assertThat(createdIds).contains(contractId.toString());
                assertThat(contractResult.getFunctionParameters()).isEqualTo("0x");
                break;
            case CALL:
                numCreatedIds = 1;
                assertThat(createdIds).doesNotContain(contractId.toString());
                assertThat(contractResult.getFunctionParameters()).isNotEqualTo("0x");
                break;
            default:
                break;
        }

        assertThat(contractResult.getAmount()).isEqualTo(amount);
        assertThat(createdIds).hasSize(numCreatedIds);
    }

    private void executeCreateChildTransaction(int transferAmount) {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                maxFunctionGas,
                "createChild",
                new ContractFunctionParameters()
                        .addUint256(BigInteger.valueOf(transferAmount)),
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private enum ContractExecutionStage {
        CREATION,
        CALL
    }
}
