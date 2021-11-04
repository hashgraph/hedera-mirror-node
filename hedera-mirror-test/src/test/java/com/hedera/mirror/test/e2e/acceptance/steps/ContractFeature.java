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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.junit.platform.engine.Cucumber;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.ResourceUtils;

import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.ContractInfo;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
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
public class ContractFeature {
    private static final int MAX_FILE_SIZE = 5500; // ensure transaction bytes are under 6144 (6kb)
    private final ObjectMapper mapper = new ObjectMapper();
    @Value("classpath:solidity/artifacts/contracts/MirrorNode.sol/MirrorNode.json")
    Path mirrorNodeContract;
    @Value("classpath:solidity/artifacts/contracts/Parent.sol/Parent.json")
    Path parentContract;
    @Autowired
    private ContractClient contractClient;
    @Autowired
    private FileClient fileClient;
    @Autowired
    private MirrorNodeClient mirrorClient;

    private NetworkTransactionResponse networkTransactionResponse;
    private ContractId contractId;
    private FileId fileId;
    private ContractName contractName;

    @Given("I successfully create a contract from {string} contract bytes")
    public void createNewContract(String contractFile) throws IOException {
        switch (ContractName.valueOf(contractFile)) {
            case MIRROR_NODE:
                CompiledSolidityArtifact compiledMirrorNodeArtifact = mapper.readValue(
                        ResourceUtils.getFile(mirrorNodeContract.toUri()),
                        CompiledSolidityArtifact.class);
                createMirrorNodeContract(compiledMirrorNodeArtifact.getBytecode());
                contractName = ContractName.MIRROR_NODE;
                break;
            case PARENT:
            default:
                CompiledSolidityArtifact compiledParentArtifact = mapper.readValue(
                        ResourceUtils.getFile(parentContract.toUri()),
                        CompiledSolidityArtifact.class);
                createParentContract(compiledParentArtifact.getBytecode());
                contractName = ContractName.PARENT;
                break;
        }
    }

    @Given("I successfully call the contract")
    public void callContract() {
        switch (contractName) {
            case MIRROR_NODE:
                // submit a transaction and verify shard and transaction count are increased
                executeContractSubmitTransaction(
                        1234,
                        "CRYPTOTRANSFER",
                        1,
                        Hbar.fromTinybars(10000000));
                callContractGetShardCount(1);
                callContractGetTransactionCount(1);
                break;
            case PARENT:
                // create child contract, verify balance, transfer amount and verify update balance
                callGetBalance(0);
                executeCreateChildTransaction();
                callGetChildBalance(0);
                int donationAmount = 10000000;
                int transferAmount = 1234;
                executeDonateTransaction(donationAmount);
                int initialParentBalance = callGetBalance(donationAmount);
                callTransferToChild(transferAmount);
                int childBalance = callGetChildBalance(transferAmount);
                callGetBalance(donationAmount - transferAmount);
                break;
            default:
                break;
        }
    }

    @Given("I successfully update the contract")
    public void updateContract() {
        networkTransactionResponse = contractClient.updateContract(contractId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully delete the contract")
    public void deleteContract() {
        networkTransactionResponse = contractClient.deleteContract(contractId);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the network confirms contract presence")
    public void verifyNetworkContractCreateResponse() {
        var contractInfo = contractClient.getContractInfo(contractId);

        verifyContractInfo(contractInfo);
        assertThat(contractInfo.balance.toTinybars()).isEqualTo(0);
        assertThat(contractInfo.isDeleted).isFalse();
    }

    @Then("the network confirms contract update")
    public void verifyNetworkContractUpdateResponse() {
        var contractInfo = contractClient.getContractInfo(contractId);

        verifyContractInfo(contractInfo);
        assertThat(contractInfo.balance.toTinybars()).isEqualTo(0);
        assertThat(contractInfo.isDeleted).isFalse();
    }

    @Then("the network confirms contract absence")
    public void verifyNetworkContractDeleteResponse() {
        assertThrows(PrecheckStatusException.class, () -> contractClient
                .getContractInfo(contractId), "CONTRACT_DELETED");
    }

    @Then("the mirror node REST API should return status {int} for the contract transaction")
    public void verifyMirrorAPIResponses(int status) {
        log.info("Verify contract transaction");
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorTransactionsResponse, status);

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
        byte[] contractBytes = contractContents.getBytes(StandardCharsets.UTF_8);
        int byteIndex = 0;
        boolean fileCreateOrUpdate = true;
        while (byteIndex <= contractBytes.length) {
            int stopIndex = byteIndex + MAX_FILE_SIZE;
            if (stopIndex > contractBytes.length) {
                stopIndex = contractBytes.length;
            }

            byte[] fileContents = Arrays.copyOfRange(contractBytes, byteIndex, stopIndex);
            if (fileCreateOrUpdate) {
                networkTransactionResponse = fileClient.createFile(fileContents);
            } else {
                networkTransactionResponse = fileClient.appendFile(fileId, fileContents);
            }

            assertNotNull(networkTransactionResponse.getTransactionId());
            assertNotNull(networkTransactionResponse.getReceipt());

            if (fileCreateOrUpdate) {
                fileId = networkTransactionResponse.getReceipt().fileId;
                assertNotNull(fileId);
                log.info("Created file {} to hold contract init code", fileId);
            }

            fileCreateOrUpdate = false;
            byteIndex += MAX_FILE_SIZE;
        }
    }

    private void createParentContract(String byteCode) {
        persistContractBytes(byteCode.replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                750000,
                null);

        verifyCreateContractNetworkResponse();

        callGetBalance(0);
    }

    private void createMirrorNodeContract(String byteCode) {
        persistContractBytes(byteCode.replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                750000,
                new ContractFunctionParameters()
                        .addInt8((byte) 3)
                        .addInt256(BigInteger.valueOf(100))
                        .addInt256(BigInteger.valueOf(5))
                        .addInt256(BigInteger.valueOf(1)));

        verifyCreateContractNetworkResponse();

        callContractGetStorageFee(5);
        callContractGetApiFee(1);
        callContractGetShardCount(0);
        callContractGetTransactionCount(0);
    }

    private void verifyContractInfo(ContractInfo contractInfo) {
        assertThat(contractInfo.contractMemo).isNotEmpty();
        assertThat(contractInfo.contractAccountId).isNotNull();
        assertThat(contractInfo.storage).isGreaterThan(0);
        assertThat(contractInfo.balance).isNotNull();
    }

    private MirrorTransaction verifyMirrorTransactionsResponse(MirrorTransactionsResponse mirrorTransactionsResponse,
                                                               int status) {
        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();

        return mirrorTransaction;
    }

    private MirrorContractResponse verifyContractFromMirror(boolean isDeleted) {
        MirrorContractResponse mirrorContract = mirrorClient.getContractInfo(contractId.toString());

        assertNotNull(mirrorContract);
        assertThat(mirrorContract.getContractId()).isEqualTo(contractId.toString());
        assertThat(mirrorContract.getFileId()).isEqualTo(fileId.toString());
        assertThat(mirrorContract.isDeleted()).isEqualTo(isDeleted);

        return mirrorContract;
    }

    private void executeContractSubmitTransaction(long timestamp, String transactionType, int shard,
                                                  Hbar paymentAmount) {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                300000L,
                "submitTransaction",
                new ContractFunctionParameters()
                        .addUint256(BigInteger.valueOf(timestamp))
                        .addString(transactionType)
                        .addUint32(shard),
                paymentAmount);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void executeCreateChildTransaction() {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                70000,
                "createChild",
                null,
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void executeDonateTransaction(int sponsorAmount) {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                5000,
                "donate",
                null,
                Hbar.fromTinybars(sponsorAmount));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void callTransferToChild(int transferAmount) {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                20000,
                "transferToChild",
                new ContractFunctionParameters()
                        .addUint256(BigInteger.valueOf(transferAmount)),
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private int callGetBalance(int balanceFloor) {
        log.debug("Confirm contract '{}' parent callGetBalance gets a valid balance",
                contractId);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                500,
                "getBalance",
                null,
                null);

        verifyContractFunctionResult(contractFunctionResult);
        int balance = contractFunctionResult.getInt256(0).intValue();
        assertThat(balance).isGreaterThanOrEqualTo(balanceFloor);
        log.trace("getBalance parent balance is {}", balance);
        return balance;
    }

    private int callGetChildBalance(int balanceFloor) {
        log.debug("Confirm contract '{}' getChildBalance gets a valid balance",
                contractId);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                7000,
                "getChildBalance",
                null,
                null);

        verifyContractFunctionResult(contractFunctionResult);
        int balance = contractFunctionResult.getInt256(0).intValue();
        assertThat(balance).isGreaterThanOrEqualTo(balanceFloor);
        log.trace("getChildBalance child balance is {}", balance);
        return balance;
    }

    private void callContractGetTransactionCount(int expectedCount) {
        log.debug("Confirm contract '{}' transaction count is {}", contractId, expectedCount);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                750000,
                "getTransactionCount",
                null,
                null);

        verifyContractFunctionResult(contractFunctionResult);
        assertThat(contractFunctionResult.getInt256(0)).isEqualTo(expectedCount);
    }

    private void callContractGetApiFee(int expectedCount) {
        log.debug("Confirm contract '{}' api fee is {}", contractId, expectedCount);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                750000,
                "getApiFee",
                null,
                null);

        verifyContractFunctionResult(contractFunctionResult);
        assertThat(contractFunctionResult.getInt256(0)).isEqualTo(expectedCount);
    }

    private void callContractGetStorageFee(int expectedAmount) {
        log.debug("Confirm contract '{}' storage fee is {}", contractId, expectedAmount);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                750000,
                "getStorageFee",
                null,
                null);

        verifyContractFunctionResult(contractFunctionResult);
        assertThat(contractFunctionResult.getInt256(0)).isEqualTo(expectedAmount);
    }

    private void callContractGetShardCount(int expectedAmount) {
        log.debug("Confirm contract '{}' shard count is {}", contractId, expectedAmount);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                750000,
                "getShardCount",
                null,
                null);

        verifyContractFunctionResult(contractFunctionResult);
        assertThat(contractFunctionResult.getInt256(0)).isEqualTo(expectedAmount);
    }

    private void verifyContractFunctionResult(ContractFunctionResult contractFunctionResult) {
        assertNotNull(contractFunctionResult);
        assertThat(contractFunctionResult.contractId).isEqualTo(contractId);
        assertThat(contractFunctionResult.errorMessage).isNullOrEmpty();
        assertThat(contractFunctionResult.gasUsed).isGreaterThan(0);
        assertThat(contractFunctionResult.logs.size()).isZero();
    }

    @RequiredArgsConstructor
    public enum ContractName {
        MIRROR_NODE,
        PARENT
    }
}
