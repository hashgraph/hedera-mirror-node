/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.test.e2e.acceptance.client.NetworkAdapter.BIG_INTEGER_TUPLE;
import static com.hedera.mirror.test.e2e.acceptance.client.NetworkAdapter.BYTES_TUPLE;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_CALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_DESTRUCT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.COPY_CODE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.DESTROY_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CODE_HASH;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CODE_SIZE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.ContractFunctionParameters;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import com.hedera.mirror.test.e2e.acceptance.util.TestUtil;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigInteger;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EquivalenceFeature extends AbstractFeature {
    private static final String OBTAINER_SAME_CONTRACT_ID_EXCEPTION = "OBTAINER_SAME_CONTRACT_ID";
    private static final String INVALID_SOLIDITY_ADDRESS_EXCEPTION = "INVALID_SOLIDITY_ADDRESS";
    private static final String TRANSACTION_SUCCESSFUL_MESSAGE = "Transaction successful";

    private DeployedContract equivalenceDestructContract;
    private DeployedContract equivalenceCallContract;

    private String equivalenceDestructContractSolidityAddress;

    @Given("I successfully create selfdestruct contract")
    public void createNewSelfDestructContract() {
        equivalenceDestructContract = getContract(EQUIVALENCE_DESTRUCT);
        equivalenceDestructContractSolidityAddress =
                equivalenceDestructContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create equivalence call contract")
    public void createNewEquivalenceCallContract() {
        equivalenceCallContract = getContract(EQUIVALENCE_CALL);
    }

    @Then("the mirror node REST API should return status {int} for the contracts creation")
    public void verifyMirrorAPIResponse(int status) {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, status);
        }
    }

    @Then("I execute selfdestruct and set beneficiary to {string} address with call to {node}")
    public void selfDestructAndSetBeneficiary(String beneficiary, NodeNameEnum node) {
        var accountId = new AccountId(extractAccountNumber(beneficiary));

        var data = encodeData(EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, TestUtil.asAddress(accountId));
        var functionResult =
                callContract(node, StringUtils.EMPTY, EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, data, TupleType.EMPTY);

        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);

        final var message = functionResult.getResultAsText();
        if (extractAccountNumber(beneficiary) < 751) {
            assertEquals(INVALID_SOLIDITY_ADDRESS_EXCEPTION, message);
        } else {
            assertEquals(TRANSACTION_SUCCESSFUL_MESSAGE, message);
        }
    }

    @Then("I execute balance opcode to system account {string} address would return 0 with call to {node}")
    public void balanceOfAddress(String address, NodeNameEnum node) {
        final var accountId = new AccountId(extractAccountNumber(address));
        var data = encodeData(EQUIVALENCE_CALL, GET_BALANCE, TestUtil.asAddress(accountId));
        var functionResult =
                callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_BALANCE, data, BIG_INTEGER_TUPLE);
        if (extractAccountNumber(address) < 751) {
            assertEquals(BigInteger.ZERO, functionResult.getResultAsNumber());
        } else {
            assertTrue(functionResult.getResultAsNumber().longValue() > 1);
        }
    }

    @Then("I execute balance opcode against a contract with balance with call to {node}")
    public void balanceOfContract(NodeNameEnum node) {
        var data =
                encodeData(EQUIVALENCE_CALL, GET_BALANCE, TestUtil.asAddress(equivalenceDestructContract.contractId()));
        var functionResult =
                callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_BALANCE, data, BIG_INTEGER_TUPLE);
        assertEquals(new BigInteger("10000"), functionResult.getResultAsNumber());
    }

    @Then("I verify extcodesize opcode against a system account {string} address returns 0 with call to {node}")
    public void extCodeSizeAgainstSystemAccount(String address, NodeNameEnum node) {
        final var accountId = new AccountId(extractAccountNumber(address));
        var data = encodeData(EQUIVALENCE_CALL, GET_CODE_SIZE, TestUtil.asAddress(accountId));
        var functionResult =
                callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_CODE_SIZE, data, BIG_INTEGER_TUPLE);
        assertEquals(BigInteger.ZERO, functionResult.getResultAsNumber());
    }

    @Then(
            "I verify extcodecopy opcode against a system account {string} address returns empty bytes with call to {node}")
    public void extCodeCopyAgainstSystemAccount(String address, NodeNameEnum node) {
        final var accountId = new AccountId(extractAccountNumber(address));
        var data = encodeData(EQUIVALENCE_CALL, COPY_CODE, TestUtil.asAddress(accountId));
        var functionResult = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, COPY_CODE, data, BYTES_TUPLE);
        assertEquals("", functionResult.getResultAsText());
    }

    @Then(
            "I verify extcodehash opcode against a system account {string} address returns empty bytes with call to {node}")
    public void extCodeHashAgainstSystemAccount(String address, NodeNameEnum node) {
        final var accountId = new AccountId(extractAccountNumber(address));
        var data = encodeData(EQUIVALENCE_CALL, GET_CODE_HASH, TestUtil.asAddress(accountId));
        var functionResult = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_CODE_HASH, data, BYTES_TUPLE);
        assertArrayEquals(new byte[0], functionResult.getResultAsBytes().toArray());
    }

    @Then("I execute selfdestruct and set beneficiary to the deleted contract address")
    public void selfDestructAndSetBeneficiaryToDeletedContract() {
        var parameters = new ContractFunctionParameters().addAddress(equivalenceDestructContractSolidityAddress);
        var message = executeContractCallTransaction(equivalenceDestructContract, "destroyContract", parameters);
        removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        var extractedStatus = extractStatus(message);
        assertEquals(OBTAINER_SAME_CONTRACT_ID_EXCEPTION, extractedStatus);
    }

    public static long extractAccountNumber(String account) {
        String[] parts = account.split("\\.");
        return Long.parseLong(parts[parts.length - 1]);
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
            return e.getMessage();
        }
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

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        GET_BALANCE("getBalance"),
        DESTROY_CONTRACT("destroyContract"),
        COPY_CODE("copyCode"),
        GET_CODE_SIZE("getCodeSize"),
        GET_CODE_HASH("getCodeHash");

        private final String selector;
    }
}
