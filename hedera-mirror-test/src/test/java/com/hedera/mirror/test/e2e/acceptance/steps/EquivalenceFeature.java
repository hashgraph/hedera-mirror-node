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
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.Selectors.HTS_APPROVE;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.hexToAscii;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractFunctionResult;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EquivalenceFeature extends AbstractFeature {
    private final TokenClient tokenClient;
    private static final String OBTAINER_SAME_CONTRACT_ID_EXCEPTION = "OBTAINER_SAME_CONTRACT_ID";
    private static final String INVALID_SOLIDITY_ADDRESS_EXCEPTION = "INVALID_SOLIDITY_ADDRESS";
    private static final String INVALID_FEE_SUBMITTED = "INVALID_FEE_SUBMITTED";
    private static final String TRANSACTION_SUCCESSFUL_MESSAGE = "Transaction successful";

    private DeployedContract deployedEquivalenceDestruct;
    private DeployedContract deployedEquivalenceCall;
    private DeployedContract deployedPrecompileContract;

    private String equivalenceDestructContractSolidityAddress;
    private String equivalenceCallContractSolidityAddress;
    private String precompileContractSolidityAddress;

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

    @Given("I successfully create estimate precompile contract")
    public void createNewEstimatePrecompileContract() throws IOException {
        deployedPrecompileContract = getContract(ESTIMATE_PRECOMPILE);
        precompileContractSolidityAddress = deployedEquivalenceCall.contractId().toSolidityAddress();
    }

    @Then("the mirror node REST API should return status {int} for the contracts creation")
    public void verifyMirrorAPIResponse(int status) {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, status);
        }
    }

    @Then("I execute selfdestruct and set beneficiary to invalid {string} address")
    public void selfDestructAndSetBeneficiary(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceDestruct, "destroyContract", parameters);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals(INVALID_SOLIDITY_ADDRESS_EXCEPTION, extractedStatus);
    }

    @Then("I execute selfdestruct and set beneficiary to valid {string} address")
    public void selfDestructAndSetBeneficiaryValid(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(deployedEquivalenceDestruct, "destroyContract", parameters);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute selfdestruct and set beneficiary to the deleted contract address")
    public void selfDestructAndSetBeneficiaryToDeletedContract() {
        var parameters = new ContractFunctionParameters().addAddress(equivalenceDestructContractSolidityAddress);
        var message = executeContractCallTransaction(deployedEquivalenceDestruct, "destroyContract", parameters);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals(OBTAINER_SAME_CONTRACT_ID_EXCEPTION, extractedStatus);
    }

    @Then("I execute balance opcode to system account {string} address would return 0")
    public void balanceOfAddress(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getBalance", parameters);
        assertEquals(new BigInteger("0"), functionResult.getInt256(0));
    }

    @Then("I execute balance opcode against a contract with balance")
    public void balanceOfContract() {
        var parameters = new ContractFunctionParameters().addAddress(equivalenceDestructContractSolidityAddress);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getBalance", parameters);
        assertEquals(new BigInteger("10000"), functionResult.getInt256(0));
    }

    @Then("I verify extcodesize opcode against a system account {string} address returns 0")
    public void extCodeSizeAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getCodeSize", parameters);
        assertEquals(new BigInteger("0"), functionResult.getInt256(0));
    }

    @Then("I verify extcodecopy opcode against a system account {string} address returns empty bytes")
    public void extCodeCopyAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "copyCode", parameters);
        assertArrayEquals(new byte[0], functionResult.getBytes(0));
    }

    @Then("I verify extcodehash opcode against a system account {string} address returns empty bytes")
    public void extCodeHashAgainstSystemAccount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId);
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getCodeHash", parameters);
        assertArrayEquals(new byte[0], functionResult.getBytes(0));
    }

    @And("I associate {token} to contract")
    public void associateTokenToContract(TokenNameEnum tokenName) throws InvalidProtocolBufferException {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        tokenClient.associate(deployedEquivalenceCall.contractId(), tokenId);
    }

    @Then("I execute directCall to {string} address without amount")
    public void directCallToZeroAddressWithoutAmount(String address) {
        var contractId = new ContractId(extractAccountNumber(address));
        var message = makeDirectCall(contractId, "destroyContract", null, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_CONTRACT_ID", extractedStatus);
    }

    @Then("I execute directCall to {string} address with amount {int}")
    public void directCallToZeroAddressWithAmount(String address, int amount) {
        var contractId = new ContractId(extractAccountNumber(address));
        var message = makeDirectCall(contractId, "destroyContract", null, Hbar.fromTinybars(amount));
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals("INVALID_CONTRACT_ID", extractedStatus);
    }

    @Then("I execute internal call against HTS precompile with approve function for {token} without amount")
    public void executeInternalCallForHTSApprove(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeDataToByteArray(
                HTS_APPROVE,
                asAddress(tokenId),
                asAddress(equivalenceCallContractSolidityAddress),
                new BigInteger("10"));
        var parameters = new ContractFunctionParameters()
                .addAddress("0x0000000000000000000000000000000000000167")
                .addBytes(data);
        var message = executeContractCallTransaction(deployedEquivalenceCall, "makeCallWithoutAmount", parameters);
        assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
    }

    @Then("I execute internal call against HTS precompile with approve function for {token} with amount")
    public void executeInternalCallForHTSApproveWithAmount(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var data = encodeDataToByteArray(
                HTS_APPROVE,
                asAddress(tokenId),
                asAddress(equivalenceCallContractSolidityAddress),
                new BigInteger("10"));
        var parameters = new ContractFunctionParameters()
                .addAddress("0x0000000000000000000000000000000000000167")
                .addBytes(data);
        var functionResult = executeContractCallTransaction(
                deployedEquivalenceCall, "makeCallWithoutAmount", parameters, Hbar.fromTinybars(10L));
        // POTENTIAL BUG
        // THIS RETURNS CONTRACT REVERT EXECUTED - > WE EXPECT INVALID_FEE_SUBMITTED
    }

    @Then("I execute internal call against PRNG precompile address without amount")
    public void executeInternalCallForPRNGWithoutAmount() {
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "getPseudorandomSeed");
        assertEquals(32, functionResult.getBytes32(0).length);
    }

    @Then("I execute internal call against PRNG precompile address with amount")
    public void executeInternalCallForPRNGWithAmount() {
        var functionResult = executeContractCallTransaction(
                deployedEquivalenceCall, "getPseudorandomSeedWithAmount", null, Hbar.fromTinybars(10L));
        // POTENTIAL BUG
        // THIS RETURNS SUCCESS FOR THE CHILD RECORD - > WE EXPECT INVALID_FEE_SUBMITTED
    }

    @Then("I execute internal call against exchange rate precompile address without amount")
    public void executeInternalCallForExchangeRateWithoutAmount() {
        var parameters = new ContractFunctionParameters().addUint256(new BigInteger("100"));
        var functionResult = executeContractCallQuery(deployedEquivalenceCall, "exchangeRateWithoutAmount", parameters);
        assertTrue(functionResult.getUint256(0).longValue() > 1);
    }

    @Then("I execute internal call against exchange rate precompile address with amount")
    public void executeInternalCallForExchangeRateWithAmount() {
        var parameters = new ContractFunctionParameters().addUint256(new BigInteger("100"));
        var functionResult = executeContractCallTransaction(
                deployedEquivalenceCall, "exchangeRateWithAmount", parameters, Hbar.fromTinybars(10L));
        // POTENTIAL BUG
        // THIS RETURNS SUCCESS FOR THE CHILD RECORD - > WE EXPECT INVALID_FEE_SUBMITTED
        // AMOUNT REACHES ONLY THE CONTRACT(deployedEquivalenceCall)
    }

    @Then("I make internal call to system account {string} with amount")
    public void internalCallToSystemAddressWithAmount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId).addBytes(new byte[0]);
        var functionResult = executeContractCallTransaction(
                deployedEquivalenceCall, "makeCallWithAmount", parameters, Hbar.fromTinybars(100L));
        // POTENTIAL BUG
        // THIS RETURNS FAILURE FOR CHILD RECORD WITH PRECOMPILE_ERROR - > WE EXPECT INVALID_FEE_SUBMITTED
        // TOP LEVEL TRANSACTION IS SUCCESS
        // WAITING TO BE CLARIFIED
    }

    @Then("I make internal call to system account {string} without amount")
    public void internalCallToSystemAddressWithoutAmount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId).addBytes(new byte[0]);
        var functionResult =
                executeContractCallTransaction(deployedEquivalenceCall, "makeCallWithoutAmount", parameters);
        // POTENTIAL BUG
        // THIS RETURNS FAILURE FOR CHILD RECORD WITH PRECOMPILE_ERROR - > WE EXPECT INVALID_SOLIDITY_ADDRESS
        // TOP LEVEL TRANSACTION IS SUCCESS
        // WAITING TO BE CLARIFIED
    }

    @Then("I make internal call to ethereum precompile {string} address with amount")
    public void internalCallToEthPrecompileWithAmount(String address) {
        var accountId = new AccountId(extractAccountNumber(address)).toSolidityAddress();
        var parameters = new ContractFunctionParameters().addAddress(accountId).addBytes(new byte[0]);
        var transactionId = executeContractCallTransactionAndReturnId(
                deployedEquivalenceCall, "makeCallWithAmount", parameters, Hbar.fromTinybars(10L));
        var message = extractInternalCallErrorMessage(transactionId);
        assertEquals(INVALID_FEE_SUBMITTED, message);
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

    public String extractInternalCallErrorMessage(String transactionId) throws IllegalArgumentException {
        var actions = mirrorClient.getContractActions(transactionId).getActions();
        if (actions == null || actions.size() < 2) {
            throw new IllegalArgumentException("The actions list must contain at least two elements.");
        }

        String hexString = actions.get(1).getResultData();
        return hexToAscii(hexString.replace("0x", ""));
    }

    private String executeContractCallTransaction(
            DeployedContract deployedContract,
            String functionName,
            ContractFunctionParameters parameters,
            Hbar payableAmount) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(),
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

    private String executeContractCallTransaction(
            DeployedContract deployedContract, String functionName, ContractFunctionParameters parameters) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(),
                    contractClient
                            .getSdkClient()
                            .getAcceptanceTestProperties()
                            .getFeatureProperties()
                            .getMaxContractFunctionGas(),
                    functionName,
                    parameters,
                    null);

            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
            return "Transaction successful";
        } catch (Exception e) {
            // Return the exception message
            return e.getMessage();
        }
    }

    private String makeDirectCall(
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

    private String executeContractCallTransactionAndReturnId(
            DeployedContract deployedContract,
            String functionName,
            ContractFunctionParameters parameters,
            Hbar payableAmount) {
        ExecuteContractResult executeContractResult = contractClient.executeContract(
                deployedContract.contractId(),
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
        return networkTransactionResponse.getTransactionIdStringNoCheckSum();
    }

    private ContractFunctionResult executeContractCallQuery(
            DeployedContract deployedContract, String functionName, ContractFunctionParameters parameters) {
        return contractClient.executeContractQuery(
                deployedContract.contractId(),
                functionName,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                parameters);
    }

    private ContractFunctionResult executeContractCallQuery(DeployedContract deployedContract, String functionName) {
        return contractClient.executeContractQuery(
                deployedContract.contractId(),
                functionName,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                null);
    }

    @Getter
    @RequiredArgsConstructor
    enum Selectors implements SelectorInterface {
        HTS_APPROVE("approve(address,address,uint256)");

        private final String selector;
    }
}
