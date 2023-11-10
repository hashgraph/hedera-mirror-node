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

import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_DESTRUCT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EquivalenceFeature extends AbstractFeature {
    private DeployedContract deployedEquivalenceDestruct;
    private String equivalenceContractSolidityAddress;

    @Given("I successfully create in-equivalence contract")
    public void createNewEstimateContract() throws IOException {
        deployedEquivalenceDestruct = getContract(EQUIVALENCE_DESTRUCT);
        equivalenceContractSolidityAddress =
                deployedEquivalenceDestruct.contractId().toSolidityAddress();
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
        assertEquals(extractedStatus, "INVALID_SOLIDITY_ADDRESS");
    }

    @Then("I execute selfdestruct and set beneficiary to valid {string} address")
    public void selfDestructAndSetBeneficiaryValid(String beneficiary) {
        var accountId = new AccountId(extractAccountNumber(beneficiary)).toSolidityAddress();
        ContractFunctionParameters parameters = new ContractFunctionParameters().addAddress(accountId);
        var message = executeContractCallTransaction(
                deployedEquivalenceDestruct.contractId(), "destroyContract", parameters, null);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        assertEquals(message, "Transaction successful");
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
            return "Transaction successful"; // Or any other success message or relevant data
        } catch (Exception e) {
            // Return the exception message
            return e.getMessage();
        }
    }
}
