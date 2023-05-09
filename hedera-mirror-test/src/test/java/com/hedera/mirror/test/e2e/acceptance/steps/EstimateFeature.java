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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ResourceUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesStringRightPad;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EstimateFeature extends AbstractFeature {
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    /**
     * Estimate gas values are hardcoded at this moment until we get better solution such as actual gas used returned
     * from the consensus node. It will be changed in future PR when actualGasUsed field is added to the protobufs.
     */
    private static final String MULTIPLY_SIMPLE_NUMBERS_SELECTOR = "0ec1551d";
    private static final int MULTIPLY_SIMPLE_NUMBERS_ACTUAL_GAS = 21227;
    private static final String MESSAGE_SENDER_SELECTOR = "d737d0c7";
    private static final int MESSAGE_SENDER_ACTUAL_GAS = 21290;
    private static final String TX_ORIGIN_SELECTOR = "f96757d1";
    private static final int TX_ORIGIN_ACTUAL_GAS = 21289;
    private static final String MESSAGE_VALUE_SELECTOR = "ddf363d7";
    private static final int MESSAGE_VALUE_ACTUAL_GAS = 21234;
    private static final String MESSAGE_SIGNER_SELECTOR = "ec3e88cf";
    private static final int MESSAGE_SIGNER_ACTUAL_GAS = 21252;
    private static final String ADDRESS_BALANCE_SELECTOR = "3ec4de35";
    private static final int ADDRESS_BALANCE_ACTUAL_GAS = 24030;
    private static final String UPDATE_COUNTER_SELECTOR = "c648049d";
    private static final int UPDATE_COUNTER_ACTUAL_GAS = 26279;
    private static final String DEPLOY_CONTRACT_VIA_CREATE_OPCODE_SELECTOR = "6e6662b9";
    private static final int DEPLOY_CONTRACT_VIA_CREATE_OPCODE_ACTUAL_GAS = 53477;
    private static final String DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE_SELECTOR = "dbb6f04a";
    private static final int DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE_ACTUAL_GAS = 55693;
    private static final String STATIC_CALL_TO_CONTRACT_SELECTOR = "ef0a4eac";
    private static final int STATIC_CALL_TO_CONTRACT_ACTUAL_GAS = 26416;
    private static final String DELEGATE_CALL_TO_CONTRACT_SELECTOR = "d3b6e741";
    private static final int DELEGATE_CALL_TO_CONTRACT_ACTUAL_GAS = 26417;
    private static final String CALL_CODE_TO_CONTRACT_SELECTOR = "ac7e2758";
    private static final int CALL_CODE_TO_CONTRACT_ACTUAL_GAS = 26398;
    private static final String LOGS_SELECTOR = "74259795";
    private static final int LOGS_ACTUAL_GAS = 28757;
    private static final String DESTROY_SELECTOR = "83197ef0";
    private static final int DESTROY_ACTUAL_GAS = 51193;
    private static final String WRONG_METHOD_SIGNATURE_SELECTOR = "ffffffff";
    private static final String CALL_TO_INVALID_CONTRACT_SELECTOR = "70079963";
    private static final int CALL_TO_INVALID_CONTRACT_ACTUAL_GAS = 24374;
    private static final String DELEGATE_CALL_TO_INVALID_CONTRACT_SELECTOR = "7df6ee27";
    private static final int DELEGATE_CALL_TO_INVALID_CONTRACT_ACTUAL_GAS = 24350;
    private static final String STATIC_CALL_TO_INVALID_CONTRACT_SELECTOR = "41f32f0c";
    private static final int STATIC_CALL_TO_INVALID_CONTRACT_ACTUAL_GAS = 24394;
    private static final String CALL_CODE_TO_INVALID_CONTRACT_SELECTOR = "e080b4aa";
    private static final int CALL_CODE_TO_INVALID_CONTRACT_ACTUAL_GAS = 24031;
    private static final String CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_SELECTOR = "4929af37";
    private static final int CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_ACTUAL_GAS = 26100;
    private static final String DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_SELECTOR = "80f009b6";
    private static final int DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_ACTUAL_GAS = 24712;
    private static final String CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW_SELECTOR = "fa5e414e";
    private static final int CALL_CODE_TO_EXTERNAL_CONTRACT_VIEW_FUNCTION_ACTUAL_GAS = 22272;
    private static final String STATE_UPDATE_OF_CONTRACT_SELECTOR = "5256b99d";
    private static final int STATE_UPDATE_OF_CONTRACT_ACTUAL_GAS = 30500;
    private static final String GET_GAS_LEFT_SELECTOR = "51be4eaa";
    private static final int GET_GAS_LEFT_ACTUAL_GAS = 21326;
    private static final String REENTRANCY_TRANSFER_ATTACK_SELECTOR = "ffaf0890";
    private static final int REENTRANCY_TRANSFER_ATTACK_ACTUAL_GAS = 55500;
    private static final String REENTRANCY_CALL_ATTACK_SELECTOR = "e7df080e";
    private static final int REENTRANCY_CALL_ATTACK_ACTUAL_GAS = 55818;
    private static final String NESTED_CALLS_SELECTOR = "bb376a96";
    private static final int POSITIVE_NESTED_CALLS_ACTUAL_GAS = 45871;
    private static final int LIMITED_NESTED_CALLS_ACTUAL_GAS = 525255;
    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final String GET_ADDRESS_SELECTOR = "38cc4831";
    private static final String INVALID_SELECTOR = "5a790dba";
    private static final String GET_MOCK_ADDRESS_SELECTOR = "14a7862c";


    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private DeployedContract deployedContract;
    private String contractSolidityAddress;
    private int lowerDeviation;
    private int upperDeviation;


    @Value("classpath:solidity/artifacts/contracts/EstimateGasContract.sol/EstimateGasContract.json")
    private Path estimateGasTestContract;


    private CompiledSolidityArtifact compiledSolidityArtifacts;
    private String newAccountEvnAddress;

    /**
     * Checks if the actualUsedGas is within the specified range of the estimatedGas.
     * <p>
     * The method calculates the lower and upper bounds as percentages of the actualUsedGas, then checks if the
     * estimatedGas is within the range (inclusive) and returns true if it is, otherwise returns false.
     *
     * @param actualUsedGas     the integer value that represents the expected value
     * @param estimatedGas      the integer value to be checked
     * @param lowerBoundPercent the integer percentage value for the lower bound of the acceptable range
     * @param upperBoundPercent the integer percentage value for the upper bound of the acceptable range
     * @return true if the actualUsedGas is within the specified range, false otherwise
     */
    public static boolean isWithinDeviation(int actualUsedGas, int estimatedGas, int lowerBoundPercent,
                                            int upperBoundPercent) {
        int lowerDeviation = actualUsedGas * lowerBoundPercent / 100;
        int upperDeviation = actualUsedGas * upperBoundPercent / 100;

        int lowerBound = actualUsedGas + lowerDeviation;
        int upperBound = actualUsedGas + upperDeviation;

        return (estimatedGas >= lowerBound) && (estimatedGas <= upperBound);
    }

    @Before
    public void initialization() throws IOException {
        compiledSolidityArtifacts = MAPPER.readValue(ResourceUtils.getFile(estimateGasTestContract.toUri()),
                CompiledSolidityArtifact.class);
    }

    @Given("I successfully create contract from contract bytes with {int} balance")
    public void createNewEstimateContract(int supply) {
        deployedContract = createContract(compiledSolidityArtifacts, supply);
        contractSolidityAddress = deployedContract.contractId().toSolidityAddress();
        newAccountEvnAddress = PrivateKey.generateECDSA().getPublicKey().toEvmAddress().toString();
    }

    @And("lower deviation is {int}% and upper deviation is {int}%")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    @Then("I call estimateGas without arguments that multiplies two numbers")
    public void multiplyEstimateCall() {
        validateGasEstimation(MULTIPLY_SIMPLE_NUMBERS_SELECTOR, MULTIPLY_SIMPLE_NUMBERS_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function msgSender")
    public void msgSenderEstimateCall() {
        validateGasEstimation(MESSAGE_SENDER_SELECTOR, MESSAGE_SENDER_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function tx origin")
    public void txOriginEstimateCall() {
        validateGasEstimation(TX_ORIGIN_SELECTOR, TX_ORIGIN_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function messageValue")
    public void msgValueEstimateCall() {
        validateGasEstimation(MESSAGE_VALUE_SELECTOR, MESSAGE_VALUE_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function messageSigner")
    public void msgSignerEstimateCall() {
        validateGasEstimation(MESSAGE_SIGNER_SELECTOR, MESSAGE_SIGNER_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function balance of address")
    public void addressBalanceEstimateCall() {
        var check = RandomStringUtils.random(40, HEX_DIGITS);
        validateGasEstimation(ADDRESS_BALANCE_SELECTOR + to32BytesString(RandomStringUtils.random(40, HEX_DIGITS)),
                ADDRESS_BALANCE_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that changes contract slot information" +
            " by updating global contract field with the passed argument")
    public void updateCounterEstimateCall() {
        //update value with amount of 5
        String updateValue = to32BytesString("5");
        validateGasEstimation(UPDATE_COUNTER_SELECTOR + updateValue, UPDATE_COUNTER_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that successfully deploys a new smart contract via CREATE op code")
    public void deployContractViaCreateOpcodeEstimateCall() {
        validateGasEstimation(DEPLOY_CONTRACT_VIA_CREATE_OPCODE_SELECTOR, DEPLOY_CONTRACT_VIA_CREATE_OPCODE_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that successfully deploys a new smart contract via CREATE2 op code")
    public void deployContractViaCreateTwoOpcodeEstimateCall() {
        validateGasEstimation(DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE_SELECTOR,
                DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes a static call to a method from a different contract")
    public void staticCallToContractEstimateCall() {
        var contractCallGetMockAddress = ContractCallRequest.builder()
                .data(GET_MOCK_ADDRESS_SELECTOR)
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        var getMockAddressResponse = mirrorClient.contractsCall(contractCallGetMockAddress).getResultAsAddress();
        validateGasEstimation(STATIC_CALL_TO_CONTRACT_SELECTOR + to32BytesString(getMockAddressResponse) + to32BytesStringRightPad(GET_ADDRESS_SELECTOR), STATIC_CALL_TO_CONTRACT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes a delegate call to a method from a different contract")
    public void delegateCallToContractEstimateCall() {
        var contractCallGetMockAddress = ContractCallRequest.builder()
                .data(GET_MOCK_ADDRESS_SELECTOR)
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        var getMockAddressResponse = mirrorClient.contractsCall(contractCallGetMockAddress).getResultAsAddress();
        validateGasEstimation(DELEGATE_CALL_TO_CONTRACT_SELECTOR + to32BytesString(getMockAddressResponse) + to32BytesStringRightPad(GET_ADDRESS_SELECTOR), DELEGATE_CALL_TO_CONTRACT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes a call code to a method from a different contract")
    public void callCodeToContractEstimateCall() {
        var contractCallGetMockAddress = ContractCallRequest.builder()
                .data(GET_MOCK_ADDRESS_SELECTOR)
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        var getMockAddressResponse = mirrorClient.contractsCall(contractCallGetMockAddress).getResultAsAddress();
        validateGasEstimation(CALL_CODE_TO_CONTRACT_SELECTOR + to32BytesString(getMockAddressResponse) + to32BytesStringRightPad(GET_ADDRESS_SELECTOR), CALL_CODE_TO_CONTRACT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that performs LOG0, LOG1, LOG2, LOG3, LOG4 operations")
    public void logsEstimateCall() {
        validateGasEstimation(LOGS_SELECTOR, LOGS_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that performs self destruct")
    public void destroyEstimateCall() {
        validateGasEstimation(DESTROY_SELECTOR, DESTROY_ACTUAL_GAS);
    }

    @Then("I call estimateGas with request body that contains wrong method signature")
    public void wrongMethodSignatureEstimateCall() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(WRONG_METHOD_SIGNATURE_SELECTOR)
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(
                () -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with wrong encoded parameter")
    public void wrongEncodedParameterEstimateCall() {
        //wrong encoded address -> it should contain leading zero's equal to 64 characters
        String wrongEncodedAddress = "5642";
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ADDRESS_BALANCE_SELECTOR + wrongEncodedAddress)
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @Then("I call estimateGas with non-existing from address in the request body")
    public void wrongFromParameterEstimateCall() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(MESSAGE_SIGNER_SELECTOR)
                .to(contractSolidityAddress)
                .from(newAccountEvnAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSignerResponse =
                mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSignerResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(MESSAGE_SIGNER_ACTUAL_GAS, estimatedGas, lowerDeviation, upperDeviation));
    }

    @Then("I call estimateGas with function that makes a call to invalid smart contract")
    public void callToInvalidSmartContractEstimateCall() {
        validateGasEstimation(CALL_TO_INVALID_CONTRACT_SELECTOR + to32BytesString(RandomStringUtils.random(40, HEX_DIGITS)),
                CALL_TO_INVALID_CONTRACT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes a delegate call to invalid smart contract")
    public void delegateCallToInvalidSmartContractEstimateCall() {
        validateGasEstimation(DELEGATE_CALL_TO_INVALID_CONTRACT_SELECTOR + to32BytesString(RandomStringUtils.random(40, HEX_DIGITS)),
                CALL_TO_INVALID_CONTRACT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes a static call to invalid smart contract")
    public void staticCallToInvalidSmartContractEstimateCall() {
        validateGasEstimation(STATIC_CALL_TO_INVALID_CONTRACT_SELECTOR + to32BytesString(RandomStringUtils.random(40, HEX_DIGITS)),
                STATIC_CALL_TO_INVALID_CONTRACT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes a call code to invalid smart contract")
    public void callCodeToInvalidSmartContractEstimateCall() {
        validateGasEstimation(CALL_CODE_TO_INVALID_CONTRACT_SELECTOR + to32BytesString(RandomStringUtils.random(40, HEX_DIGITS)),
                CALL_CODE_TO_INVALID_CONTRACT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes call to an external contract function")
    public void callCodeToExternalContractFunction() {
        validateGasEstimation(CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_SELECTOR + to32BytesString("1") + to32BytesString(
                contractSolidityAddress), CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes delegate call to an external contract function")
    public void delegateCallCodeToExternalContractFunction() {
        validateGasEstimation(
                DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_SELECTOR + to32BytesString("1") + to32BytesString(
                        contractSolidityAddress), DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes call to an external contract view function")
    public void callCodeToExternalContractViewFunction() {
        validateGasEstimation(
                CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW_SELECTOR + to32BytesString("1") + to32BytesString(
                        contractSolidityAddress), CALL_CODE_TO_EXTERNAL_CONTRACT_VIEW_FUNCTION_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes a state update to a contract")
    public void stateUpdateContractFunction() {
        //making 5 times to state update
        validateGasEstimation(STATE_UPDATE_OF_CONTRACT_SELECTOR + to32BytesString("5"),
                STATE_UPDATE_OF_CONTRACT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that makes a state update to a contract several times and estimateGas is higher")
    public void progressiveStateUpdateContractFunction() {
        //making 5 times to state update
        var contractCallRequestStateUpdateWithFive = ContractCallRequest.builder()
                .data(STATE_UPDATE_OF_CONTRACT_SELECTOR + to32BytesString("5"))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse fiveStateUpdatesResponse =
                mirrorClient.contractsCall(contractCallRequestStateUpdateWithFive);
        int estimatedGasOfFiveStateUpdates = fiveStateUpdatesResponse.getResultAsNumber().intValue();
        //making 10 times to state update
        var contractCallRequestStateUpdateWithTen = ContractCallRequest.builder()
                .data(STATE_UPDATE_OF_CONTRACT_SELECTOR + to32BytesString("10"))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse tenStateUpdatesResponse =
                mirrorClient.contractsCall(contractCallRequestStateUpdateWithTen);
        int estimatedGasOfTenStateUpdates = tenStateUpdatesResponse.getResultAsNumber().intValue();
        //verifying that estimateGas for 10 state updates is higher than 5 state updates
        assertTrue(estimatedGasOfTenStateUpdates > estimatedGasOfFiveStateUpdates);
    }

    //TODO: Waiting HIP-584 implementation
    @Then("I call estimateGas with function that executes reentrancy attack with transfer")
    public void reentrancyTransferAttackFunction() {
        validateGasEstimation(
                REENTRANCY_TRANSFER_ATTACK_SELECTOR + to32BytesString(RandomStringUtils.random(40, HEX_DIGITS)) + to32BytesString(
                        "10000000000"), REENTRANCY_TRANSFER_ATTACK_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that executes reentrancy attack with call")
    public void reentrancyCallAttackFunction() {
        validateGasEstimation(
                REENTRANCY_CALL_ATTACK_SELECTOR + to32BytesString(RandomStringUtils.random(40, HEX_DIGITS)) + to32BytesString(
                        "10000000000"), REENTRANCY_CALL_ATTACK_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that executes gasLeft")
    public void getGasLeftContractFunction() {
        validateGasEstimation(GET_GAS_LEFT_SELECTOR, GET_GAS_LEFT_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that executes positive nested calls")
    public void positiveNestedCallsFunction() {
        validateGasEstimation(NESTED_CALLS_SELECTOR + to32BytesString("1") + to32BytesString("10") + to32BytesString(
                contractSolidityAddress), POSITIVE_NESTED_CALLS_ACTUAL_GAS);
    }

    @Then("I call estimateGas with function that executes limited nested calls")
    public void limitedNestedCallsFunction() {
        //verify that after exceeding a number of nested calls that the estimated gas would return the same
        //we will execute with 500, 1024 and 1025, and it should return the same estimatedGas
        validateGasEstimation(NESTED_CALLS_SELECTOR + to32BytesString("1") + to32BytesString("500") + to32BytesString(
                contractSolidityAddress), LIMITED_NESTED_CALLS_ACTUAL_GAS);
        validateGasEstimation(NESTED_CALLS_SELECTOR + to32BytesString("1") + to32BytesString("1024") + to32BytesString(
                contractSolidityAddress), LIMITED_NESTED_CALLS_ACTUAL_GAS);
        validateGasEstimation(NESTED_CALLS_SELECTOR + to32BytesString("1") + to32BytesString("1025") + to32BytesString(
                contractSolidityAddress), LIMITED_NESTED_CALLS_ACTUAL_GAS);
    }

    private DeployedContract createContract(CompiledSolidityArtifact compiledSolidityArtifact, int initialBalance) {
        var fileId = persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(fileId,
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
                null);
        var contractId = verifyCreateContractNetworkResponse();

        return new DeployedContract(fileId, contractId, compiledSolidityArtifact);
    }

    private FileId persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[]{});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        return fileId;
    }

    private ContractId verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        var contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
        return contractId;
    }

    private void validateGasEstimation(String selector, int actualGasUsed) {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(selector)
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSenderResponse =
                mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSenderResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(actualGasUsed, estimatedGas, lowerDeviation, upperDeviation));
    }

    private record DeployedContract(FileId fileId, ContractId contractId,
                                    CompiledSolidityArtifact compiledSolidityArtifact) {
    }
}
