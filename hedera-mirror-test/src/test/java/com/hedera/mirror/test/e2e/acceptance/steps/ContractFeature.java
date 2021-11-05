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
import lombok.extern.log4j.Log4j2;
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
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorContractResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Cucumber
public class ContractFeature extends StepDefinitions {
    private static final int MAX_FILE_SIZE = 5500; // ensure transaction bytes are under 6144 (6kb)

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private NetworkTransactionResponse networkTransactionResponse;
    private ContractId contractId;
    private FileId fileId;

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
        CompiledSolidityArtifact compiledParentArtifact = mapper.readValue(
                ResourceUtils.getFile(parentContract.toUri()),
                CompiledSolidityArtifact.class);
        createContract(compiledParentArtifact.getBytecode(), initialBalance);
    }

    @Given("I successfully call the contract")
    public void callContract() {
        // log and results to be verified
        executeCreateChildTransaction();
        executeDonateTransaction(1000);
        executeTransferToChild(500);
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
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorTransactionsResponse, status);
        assertThat(mirrorTransaction.getEntityId()).isEqualTo(contractId.toString());

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
    }

    @Then("the mirror node REST API should verify the deployed contract entity")
    public void verifyDeployedContractMirror() {
        verifyContractFromMirror(false);
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

//        byte[] contractBytes = contractContents.getBytes(StandardCharsets.UTF_8);
//        int byteIndex = 0;
//        boolean fileCreateOrUpdate = true;
//        while (byteIndex <= contractBytes.length) {
//            int stopIndex = byteIndex + MAX_FILE_SIZE;
//            if (stopIndex > contractBytes.length) {
//                stopIndex = contractBytes.length;
//            }
//
//            byte[] fileContents = Arrays.copyOfRange(contractBytes, byteIndex, stopIndex);
//            if (fileCreateOrUpdate) {
//                networkTransactionResponse = fileClient.createFile(fileContents);
//            } else {
//                networkTransactionResponse = fileClient.appendFile(fileId, fileContents);
//            }
//
//            assertNotNull(networkTransactionResponse.getTransactionId());
//            assertNotNull(networkTransactionResponse.getReceipt());
//
//            if (fileCreateOrUpdate) {
//                fileId = networkTransactionResponse.getReceipt().fileId;
//                assertNotNull(fileId);
//                log.info("Created file {} to hold contract init code", fileId);
//            }
//
//            fileCreateOrUpdate = false;
//            byteIndex += MAX_FILE_SIZE;
//        }
    }

    private void createContract(String byteCode, int initialBalance) {
        persistContractBytes(byteCode.replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                750000,
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                null);

        verifyCreateContractNetworkResponse();
    }

    private MirrorContractResponse verifyContractFromMirror(boolean isDeleted) {
        MirrorContractResponse mirrorContract = mirrorClient.getContractInfo(contractId.toString());

        assertNotNull(mirrorContract);
        assertThat(mirrorContract.getAdminKey()).isNotNull();
        assertThat(mirrorContract.getAutoRenewPeriod()).isNotNull();
        assertThat(mirrorContract.getBytecode()).isNotBlank();
        assertThat(mirrorContract.getContractId()).isEqualTo(contractId.toString());
        assertThat(mirrorContract.getCreatedTimestamp()).isNotBlank();
        assertThat(mirrorContract.isDeleted()).isEqualTo(isDeleted);
        assertThat(mirrorContract.getFileId()).isEqualTo(fileId.toString());
        assertThat(mirrorContract.getMemo()).isNotBlank();
        String address = mirrorContract.getSolidityAddress();
        assertThat(address).isNotBlank();
        assertThat(address).isNotEqualTo("0x");
        assertThat(address).isNotEqualTo("0x0000000000000000000000000000000000000000");
        assertThat(mirrorContract.getTimestamp()).isNotNull();

        return mirrorContract;
    }

    private void executeCreateChildTransaction() {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                57000,
                "createChild",
                null,
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void executeDonateTransaction(int sponsorAmount) {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                2500,
                "donate",
                null,
                Hbar.fromTinybars(sponsorAmount));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void executeTransferToChild(int transferAmount) {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                15000,
                "transferToChild",
                new ContractFunctionParameters()
                        .addUint256(BigInteger.valueOf(transferAmount)),
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }
}
