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
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.EquivalenceContractMethods.COPY_CODE_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.EquivalenceContractMethods.DESTROY_CONTRACT_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.EquivalenceContractMethods.GET_BALANCE_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.EquivalenceContractMethods.GET_CODE_HASH_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.EquivalenceContractMethods.GET_CODE_SIZE_SELECTOR;

import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
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
    private AcceptanceTestProperties acceptanceTestProperties;

    private DeployedContract equivalenceDestructContract;
    private DeployedContract equivalenceCallContract;

    private String equivalenceDestructContractSolidityAddress;
    private NodeAdapter nodeAdapter;
    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);

    @Given("I successfully create selfdestruct contract")
    public void createNewSelfDestructContract() throws IOException {
        equivalenceDestructContract = getContract(EQUIVALENCE_DESTRUCT);
        equivalenceDestructContractSolidityAddress =
                equivalenceDestructContract.contractId().toString();
    }

    @Given("I successfully create equivalence call contract")
    public void createNewEquivalenceCallContract() throws IOException {
        equivalenceCallContract = getContract(EQUIVALENCE_CALL);
    }

    @Then("the mirror node REST API should return status {int} for the contracts creation")
    public void verifyMirrorAPIResponse(int status) {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, status);
        }
    }

    @Then("I execute selfdestruct and set beneficiary to {string} address")
    public void selfDestructAndSetBeneficiary(final String beneficiary) {
        nodeAdapter.testCallsWithStatusResult(equivalenceCallContract, DESTROY_CONTRACT_SELECTOR, beneficiary, false);
    }

    @Then("I execute balance opcode to system account {string} address would return 0")
    public void balanceOfAddress(String address) {
        nodeAdapter.testCallsWithNumericResult(
                equivalenceCallContract, GET_BALANCE_SELECTOR, BigInteger.ZERO, address, true);
    }

    @Then("I execute balance opcode against a contract with balance")
    public void balanceOfContract() {
        nodeAdapter.testCallsWithNumericResult(
                equivalenceCallContract,
                GET_BALANCE_SELECTOR,
                THOUSAND,
                equivalenceDestructContractSolidityAddress,
                false);
    }

    @Then("I verify extcodesize opcode against a system account {string} address returns 0")
    public void extCodeSizeAgainstSystemAccount(String address) {
        nodeAdapter.testCallsWithNumericResult(
                equivalenceCallContract, GET_CODE_SIZE_SELECTOR, BigInteger.ZERO, address, false);
    }

    @Then("I verify extcodecopy opcode against a system account {string} address returns empty bytes")
    public void extCodeCopyAgainstSystemAccount(String address) {
        nodeAdapter.testCallsWithBytesResult(equivalenceCallContract, COPY_CODE_SELECTOR, address);
    }

    @Then("I verify extcodehash opcode against a system account {string} address returns empty bytes")
    public void extCodeHashAgainstSystemAccount(String address) {
        nodeAdapter.testCallsWithBytesResult(equivalenceCallContract, GET_CODE_HASH_SELECTOR, address);
    }

    @Then("I execute selfdestruct and set beneficiary to the deleted contract address")
    public void selfDestructAndSetBeneficiaryToDeletedContract() {
        nodeAdapter.testCallsWithStatusResult(
                equivalenceDestructContract,
                DESTROY_CONTRACT_SELECTOR,
                equivalenceDestructContractSolidityAddress,
                false);
    }

    @Getter
    @RequiredArgsConstructor
    public enum EquivalenceContractMethods implements SelectorInterface {
        GET_CODE_SIZE_SELECTOR("getCodeSize"),
        GET_BALANCE_SELECTOR("getBalance"),
        COPY_CODE_SELECTOR("copyCode"),
        GET_CODE_HASH_SELECTOR("getCodeHash"),
        DESTROY_CONTRACT_SELECTOR("destroyContract");

        private final String selector;
    }
}
