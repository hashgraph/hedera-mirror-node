/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_CALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_DESTRUCT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EquivalenceFeature extends AbstractFeature {
    private static final String OBTAINER_SAME_CONTRACT_ID_EXCEPTION = "OBTAINER_SAME_CONTRACT_ID";
    private static final String INVALID_SOLIDITY_ADDRESS_EXCEPTION = "INVALID_SOLIDITY_ADDRESS";
    private static final String TRANSACTION_SUCCESSFUL_MESSAGE = "Transaction successful";

    private DeployedContract deployedEquivalenceDestruct;
    private DeployedContract deployedEquivalenceCall;
    private String equivalenceDestructContractSolidityAddress;
    private String equivalenceCallContractSolidityAddress;

    @Given("I successfully create selfdestruct contract")
    public void createNewSelfDestructContract() throws IOException {
        deployedEquivalenceDestruct = getContract(EQUIVALENCE_DESTRUCT);
        equivalenceDestructContractSolidityAddress =
                deployedEquivalenceDestruct.contractId().toSolidityAddress();
    }

    @Given("I successfully create equivalence call contract")
    public void createNewEquivalenceCallContract() throws IOException {
        deployedEquivalenceCall = getContract(EQUIVALENCE_CALL);
        equivalenceCallContractSolidityAddress =
                deployedEquivalenceCall.contractId().toSolidityAddress();
    }

    @Then("the mirror node REST API should return status {int} for the contracts creation")
    public void verifyMirrorAPIResponse(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Then("I execute selfdestruct and set beneficiary to invalid {string} address")
    public void selfDestructAndSetBeneficiary(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(
                deployedEquivalenceDestruct.contractId(), "destroyContract", parameters, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals(extractedStatus, INVALID_SOLIDITY_ADDRESS_EXCEPTION);
    }

    @Then("I execute selfdestruct and set beneficiary to valid {string} address")
    public void selfDestructAndSetBeneficiaryValid(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(
                deployedEquivalenceDestruct.contractId(), "destroyContract", parameters, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        assertEquals(message, TRANSACTION_SUCCESSFUL_MESSAGE);
    }

    @Then("I execute selfdestruct and set beneficiary to the deleted contract address")
    public void selfDestructAndSetBeneficiaryToDeletedContract() {
        var parameters = new ContractFunctionParameters().addAddress(equivalenceDestructContractSolidityAddress);
        var message = executeContractCallTransaction(
                deployedEquivalenceDestruct.contractId(), "destroyContract", parameters, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals(extractedStatus, OBTAINER_SAME_CONTRACT_ID_EXCEPTION);
    }

    @Then("I execute balance opcode to system account {string} address would return 0")
    public void balanceOfAddress(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall.contractId(), "getBalance", parameters);
        assertEquals(functionResult.getInt256(0), new BigInteger("0"));
    }

    @Then("I execute balance opcode against a contract with balance")
    public void balanceOfContract() {
        var parameters = new ContractFunctionParameters().addAddress(equivalenceDestructContractSolidityAddress);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall.contractId(), "getBalance", parameters);
        assertEquals(functionResult.getInt256(0), new BigInteger("10000"));
    }

    @Then("I verify extcodesize opcode against a system account {string} address returns 0")
    public void extCodeSizeAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall.contractId(), "getCodeSize", parameters);
        assertEquals(functionResult.getInt256(0), new BigInteger("0"));
    }

    @Then("I verify extcodecopy opcode against a system account {string} address returns empty bytes")
    public void extCodeCopyAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall.contractId(), "copyCode", parameters);
        assertArrayEquals(functionResult.getBytes(0), new byte[0]);
    }

    @Then("I verify extcodehash opcode against a system account {string} address returns empty bytes")
    public void extCodeHashAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall.contractId(), "getCodeHash", parameters);
        assertArrayEquals(functionResult.getBytes(0), new byte[0]);
    }

    private static long extractAccountNumber(String account) {
        String[] parts = account.split("\\.");
        return Long.parseLong(parts[parts.length - 1]);
    }

    public static String extractStatus(String transactionResult) {
        String key = "status=";
        int statusIndex = transactionResult.indexOf(key);

        if (statusIndex != -1) {
            int startIndex = statusIndex + key.length();
            int endIndex = transactionResult.indexOf(',', startIndex);
            endIndex = endIndex != -1 ? endIndex : transactionResult.length();
            return transactionResult.substring(startIndex, endIndex);
        }

        return "Status not found";
    }

    private String executeContractCallTransaction(
            ContractId contractId, String functionName, ContractFunctionParameters parameters, Hbar payableAmount) {

        try {
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
            verifyMirrorTransactionsResponse(mirrorClient, 200);
            return "Transaction successful";
        } catch (Exception e) {
            // Return the exception message
            return e.getMessage();
        }
    }

    private ContractFunctionResult executeContractCallQuery(
            ContractId contractId, String functionName, ContractFunctionParameters parameters) {
        return contractClient.executeContractQuery(
                contractId,
                functionName,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                parameters);
    }
}
