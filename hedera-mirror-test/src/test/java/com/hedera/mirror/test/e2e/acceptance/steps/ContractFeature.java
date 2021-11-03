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
    private static final String SMART_CONTRACT_BYTECODE =
            "608060405234801561001057600080fd5b506040516104d73803806104d78339818101604052602081101561003357600080fd5b810190808051604051939291908464010000000082111561005357600080fd5b90830190602082018581111561006857600080fd5b825164010000000081118282018810171561008257600080fd5b82525081516020918201929091019080838360005b838110156100af578181015183820152602001610097565b50505050905090810190601f1680156100dc5780820380516001836020036101000a031916815260200191505b506040525050600080546001600160a01b0319163317905550805161010890600190602084019061010f565b50506101aa565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f1061015057805160ff191683800117855561017d565b8280016001018555821561017d579182015b8281111561017d578251825591602001919060010190610162565b5061018992915061018d565b5090565b6101a791905b808211156101895760008155600101610193565b90565b61031e806101b96000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c8063368b87721461004657806341c0e1b5146100ee578063ce6d41de146100f6575b600080fd5b6100ec6004803603602081101561005c57600080fd5b81019060208101813564010000000081111561007757600080fd5b82018360208201111561008957600080fd5b803590602001918460018302840111640100000000831117156100ab57600080fd5b91908080601f016020809104026020016040519081016040528093929190818152602001838380828437600092019190915250929550610173945050505050565b005b6100ec6101a2565b6100fe6101ba565b6040805160208082528351818301528351919283929083019185019080838360005b83811015610138578181015183820152602001610120565b50505050905090810190601f1680156101655780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b6000546001600160a01b0316331461018a5761019f565b805161019d906001906020840190610250565b505b50565b6000546001600160a01b03163314156101b85733ff5b565b60018054604080516020601f600260001961010087891615020190951694909404938401819004810282018101909252828152606093909290918301828280156102455780601f1061021a57610100808354040283529160200191610245565b820191906000526020600020905b81548152906001019060200180831161022857829003601f168201915b505050505090505b90565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f1061029157805160ff19168380011785556102be565b828001600101855582156102be579182015b828111156102be5782518255916020019190600101906102a3565b506102ca9291506102ce565b5090565b61024d91905b808211156102ca57600081556001016102d456fea264697066735822122084964d4c3f6bc912a9d20e14e449721012d625aa3c8a12de41ae5519752fc89064736f6c63430006000033";
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
                CompiledSolidityArtifact compiledParentArtifact = mapper.readValue(
                        ResourceUtils.getFile(parentContract.toUri()),
                        CompiledSolidityArtifact.class);
                createParentContract(compiledParentArtifact.getBytecode());
                contractName = ContractName.PARENT;
                break;
            default:
                createDefaultContract(SMART_CONTRACT_BYTECODE);
                contractName = ContractName.DEFAULT;
                break;
        }
    }

    @Given("I successfully call the contract")
    public void callContract() {
        switch (contractName) {
            case MIRROR_NODE:
                executeContractSubmitTransaction();
                callContractGetShardCount(1);
                callContractGetTransactionCount(1);
                break;
            case PARENT:
                executeCreateChildTransaction();
                callGetBalance(0);
                callGetChildBalance(0);
                executeDonateTransaction(10000000);
                callGetBalance(1);
                break;
            default:
                break;
        }
    }

    @Given("I successfully update the contract")
    public void updateContract() {
        networkTransactionResponse = fileClient.createFile(SMART_CONTRACT_BYTECODE.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);

        networkTransactionResponse = contractClient.updateContract(contractId, fileId);
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

    private void createDefaultContract(String byteCode) {
        persistContractBytes(byteCode);
        networkTransactionResponse = contractClient.createContract(
                fileId,
                750000,
                null);

        verifyCreateContractNetworkResponse();
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

    private void executeContractSubmitTransaction() {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                750000,
                "submitTransaction",
                new ContractFunctionParameters()
                        .addInt256(BigInteger.valueOf(1234))
                        .addString("CRYPTOTRANSFER")
                        .addInt8((byte) 1),
                Hbar.fromTinybars(10000000));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void executeCreateChildTransaction() {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                75000000,
                "createChild",
                null,
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void executeDonateTransaction(int sponsorAmount) {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                75000000,
                "donate",
                null,
                Hbar.fromTinybars(sponsorAmount));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void callTransferToChild(int transferAmount) {
        networkTransactionResponse = contractClient.executeContract(
                contractId,
                75000000,
                "transferToChild",
                new ContractFunctionParameters()
                        .addInt256(BigInteger.valueOf(transferAmount)),
                null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private int callGetBalance(int balanceFloor) {
        log.debug("Confirm contract '{}' parent callGetBalance gets a valid balance",
                contractId);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                750000,
                "getBalance",
                null,
                null);

        assertNotNull(contractFunctionResult);
        log.debug("getBalance contractFunctionResult, contractId: {}, gasUsed: {}, logCount: {}",
                contractFunctionResult.contractId,
                contractFunctionResult.gasUsed,
                contractFunctionResult.logs.size());
        assertThat(contractFunctionResult.contractId).isEqualTo(contractId);
        assertThat(contractFunctionResult.errorMessage).isNullOrEmpty();
        assertThat(contractFunctionResult.gasUsed).isGreaterThan(0);
        assertThat(contractFunctionResult.logs.size()).isEqualTo(0);

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
                750000,
                "getChildBalance",
                null,
                null);

        assertNotNull(contractFunctionResult);
        log.debug("getChildBalance contractFunctionResult, contractId: {}, gasUsed: {}, logCount: {}",
                contractFunctionResult.contractId,
                contractFunctionResult.gasUsed,
                contractFunctionResult.logs.size());
        assertThat(contractFunctionResult.contractId).isEqualTo(contractId);
        assertThat(contractFunctionResult.errorMessage).isNullOrEmpty();
        assertThat(contractFunctionResult.gasUsed).isGreaterThan(0);
        assertThat(contractFunctionResult.logs.size()).isEqualTo(0);

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

        assertNotNull(contractFunctionResult);
        assertThat(contractFunctionResult.contractId).isEqualTo(contractId);
        assertThat(contractFunctionResult.getInt256(0)).isEqualTo(expectedCount);
        assertThat(contractFunctionResult.errorMessage).isNullOrEmpty();
        assertThat(contractFunctionResult.gasUsed).isGreaterThan(0);
        assertThat(contractFunctionResult.logs.size()).isZero();
    }

    private void callContractGetApiFee(int expectedCount) {
        log.debug("Confirm contract '{}' api fee is {}", contractId, expectedCount);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                750000,
                "getApiFee",
                null,
                null);

        assertNotNull(contractFunctionResult);
        assertThat(contractFunctionResult.contractId).isEqualTo(contractId);
        assertThat(contractFunctionResult.getInt256(0)).isEqualTo(expectedCount);
        assertThat(contractFunctionResult.errorMessage).isNullOrEmpty();
        assertThat(contractFunctionResult.gasUsed).isGreaterThan(0);
        assertThat(contractFunctionResult.logs.size()).isZero();
    }

    private void callContractGetStorageFee(int expectedAmount) {
        log.debug("Confirm contract '{}' storage fee is {}", contractId, expectedAmount);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                750000,
                "getStorageFee",
                null,
                null);

        assertNotNull(contractFunctionResult);
        assertThat(contractFunctionResult.contractId).isEqualTo(contractId);
        assertThat(contractFunctionResult.getInt256(0)).isEqualTo(expectedAmount);
        assertThat(contractFunctionResult.errorMessage).isNullOrEmpty();
        assertThat(contractFunctionResult.gasUsed).isGreaterThan(0);
        assertThat(contractFunctionResult.logs.size()).isZero();
    }

    private void callContractGetShardCount(int expectedAmount) {
        log.debug("Confirm contract '{}' shard count is {}", contractId, expectedAmount);
        var contractFunctionResult = contractClient.callContractFunction(
                contractId,
                750000,
                "getShardCount",
                null,
                null);

        assertNotNull(contractFunctionResult);
        assertThat(contractFunctionResult.contractId).isEqualTo(contractId);
        assertThat(contractFunctionResult.getInt256(0)).isEqualTo(expectedAmount);
        assertThat(contractFunctionResult.errorMessage).isNullOrEmpty();
        assertThat(contractFunctionResult.gasUsed).isGreaterThan(0);
        assertThat(contractFunctionResult.logs.size()).isZero();
    }

    @RequiredArgsConstructor
    public enum ContractName {
        DEFAULT,
        MIRROR_NODE,
        PARENT
    }
}
