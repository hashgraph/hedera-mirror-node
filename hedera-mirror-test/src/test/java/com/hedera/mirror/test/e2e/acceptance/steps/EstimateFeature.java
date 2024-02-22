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

import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_GAS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.ADDRESS_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_INVALID_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_TO_INVALID_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_TO_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_TO_INVALID_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_BYTECODE_DATA;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_OPCODE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DESTROY;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.GET_GAS_LEFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.GET_MOCK_ADDRESS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_APPROVE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_ASSOCIATE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_DISSOCIATE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.LOGS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.MESSAGE_SENDER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.MESSAGE_SIGNER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.MESSAGE_VALUE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.MULTIPLY_NUMBERS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.NESTED_CALLS_LIMITED;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.NESTED_CALLS_POSITIVE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.REENTRANCY_CALL_ATTACK;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.STATE_UPDATE_OF_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.STATIC_CALL_TO_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.STATIC_CALL_TO_INVALID_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.TX_ORIGIN;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.UPDATE_COUNTER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.WRONG_METHOD_SIGNATURE;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.util.ModelBuilder;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Optional;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

@CustomLog
@RequiredArgsConstructor
public class EstimateFeature extends AbstractEstimateFeature {
    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final String RANDOM_ADDRESS = to32BytesString(RandomStringUtils.random(40, HEX_DIGITS));
    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private DeployedContract deployedContract;
    private String contractSolidityAddress;
    private String mockAddress;
    byte[] addressSelector;
    private TokenId fungibleTokenId;
    private String newAccountEvmAddress;
    private ExpandedAccountId receiverAccountId;

    @Given("I successfully create EstimateGas contract from contract bytes")
    public void createNewEstimateContract() throws IOException {
        deployedContract = getContract(ESTIMATE_GAS);
        contractSolidityAddress = deployedContract.contractId().toSolidityAddress();
        newAccountEvmAddress =
                PrivateKey.generateECDSA().getPublicKey().toEvmAddress().toString();
        receiverAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
    }

    @Given("I successfully create fungible token")
    public void createFungibleToken() {
        var tokenResponse = tokenClient.getToken(FUNGIBLE);
        fungibleTokenId = tokenResponse.tokenId();
        if (tokenResponse.response() != null) {
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Then("the mirror node REST API should return status {int} for the estimate contract creation")
    public void verifyMirrorAPIResponses(int status) {
        if (networkTransactionResponse != null) {
            verifyMirrorTransactionsResponse(mirrorClient, status);
        }
    }

    @And("lower deviation is {int}% and upper deviation is {int}%")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    @Then("I call estimateGas without arguments that multiplies two numbers")
    public void multiplyEstimateCall() {
        validateGasEstimation(encodeData(ESTIMATE_GAS, MULTIPLY_NUMBERS), MULTIPLY_NUMBERS, contractSolidityAddress);
    }

    @Then("I call estimateGas with function msgSender")
    public void msgSenderEstimateCall() {
        validateGasEstimation(encodeData(ESTIMATE_GAS, MESSAGE_SENDER), MESSAGE_SENDER, contractSolidityAddress);
    }

    @Then("I call estimateGas with function tx origin")
    public void txOriginEstimateCall() {
        validateGasEstimation(encodeData(ESTIMATE_GAS, TX_ORIGIN), TX_ORIGIN, contractSolidityAddress);
    }

    @Then("I call estimateGas with function messageValue")
    public void msgValueEstimateCall() {
        validateGasEstimation(encodeData(ESTIMATE_GAS, MESSAGE_VALUE), MESSAGE_VALUE, contractSolidityAddress);
    }

    @Then("I call estimateGas with function messageSigner")
    public void msgSignerEstimateCall() {
        validateGasEstimation(encodeData(ESTIMATE_GAS, MESSAGE_SIGNER), MESSAGE_SIGNER, contractSolidityAddress);
    }

    @RetryAsserts
    @Then("I call estimateGas with function balance of address")
    public void addressBalanceEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, asAddress(RANDOM_ADDRESS));
        validateGasEstimation(data, ADDRESS_BALANCE, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that changes contract slot information"
            + " by updating global contract field with the passed argument")
    public void updateCounterEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, UPDATE_COUNTER, new BigInteger("5"));
        validateGasEstimation(data, UPDATE_COUNTER, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that successfully deploys a new smart contract via CREATE op code")
    public void deployContractViaCreateOpcodeEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, DEPLOY_CONTRACT_VIA_CREATE_OPCODE);
        validateGasEstimation(data, DEPLOY_CONTRACT_VIA_CREATE_OPCODE, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that successfully deploys a new smart contract via CREATE2 op code")
    public void deployContractViaCreateTwoOpcodeEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE);
        validateGasEstimation(data, DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE, contractSolidityAddress);
    }

    @Then("I get mock contract address and getAddress selector")
    public void getMockAddress() {
        var data = encodeData(ESTIMATE_GAS, GET_MOCK_ADDRESS);
        mockAddress = callContract(data, contractSolidityAddress).getResultAsAddress();
        addressSelector = new BigInteger("0x38cc4831".substring(2), 16).toByteArray();
    }

    @Then("I call estimateGas with function that makes a static call to a method from a different contract")
    public void staticCallToContractEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, STATIC_CALL_TO_CONTRACT, asAddress(mockAddress), addressSelector);
        validateGasEstimation(data, STATIC_CALL_TO_CONTRACT, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a delegate call to a method from a different contract")
    public void delegateCallToContractEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, DELEGATE_CALL_TO_CONTRACT, asAddress(mockAddress), addressSelector);
        validateGasEstimation(data, DELEGATE_CALL_TO_CONTRACT, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a call code to a method from a different contract")
    public void callCodeToContractEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, CALL_CODE_TO_CONTRACT, asAddress(mockAddress), addressSelector);
        validateGasEstimation(data, CALL_CODE_TO_CONTRACT, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that performs LOG0, LOG1, LOG2, LOG3, LOG4 operations")
    public void logsEstimateCall() {
        validateGasEstimation(encodeData(ESTIMATE_GAS, LOGS), LOGS, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that performs self destruct")
    public void destroyEstimateCall() {
        validateGasEstimation(
                encodeData(ESTIMATE_GAS, DESTROY),
                DESTROY,
                contractSolidityAddress,
                Optional.of(contractClient.getClientAddress()));
    }

    @Then("I call estimateGas with request body that contains wrong method signature")
    public void wrongMethodSignatureEstimateCall() {
        assertContractCallReturnsBadRequest(encodeData(WRONG_METHOD_SIGNATURE), contractSolidityAddress);
    }

    @Then("I call estimateGas with wrong encoded parameter")
    public void wrongEncodedParameterEstimateCall() {
        // wrong encoded address -> it should contain leading zero's equal to 64 characters
        String wrongEncodedAddress = "5642";
        // 3ec4de35 is the address balance signature, we cant send wrong encoded parameter with headlong
        assertContractCallReturnsBadRequest("3ec4de35" + wrongEncodedAddress, contractSolidityAddress);
    }

    @Then("I call estimateGas with non-existing from address in the request body")
    public void wrongFromParameterEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, MESSAGE_SIGNER);
        var contractCallRequest = ModelBuilder.contractCallRequest()
                .data(data)
                .estimate(true)
                .from(newAccountEvmAddress)
                .to(contractSolidityAddress);
        var msgSignerResponse = callContract(contractCallRequest);
        int estimatedGas = msgSignerResponse.getResultAsNumber().intValue();

        assertWithinDeviation(MESSAGE_SIGNER.getActualGas(), estimatedGas, lowerDeviation, upperDeviation);
    }

    @Then("I call estimateGas with function that makes a call to invalid smart contract")
    public void callToInvalidSmartContractEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, CALL_TO_INVALID_CONTRACT, asAddress(RANDOM_ADDRESS));
        validateGasEstimation(data, CALL_TO_INVALID_CONTRACT, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a delegate call to invalid smart contract")
    public void delegateCallToInvalidSmartContractEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, DELEGATE_CALL_TO_INVALID_CONTRACT, asAddress(RANDOM_ADDRESS));
        validateGasEstimation(data, DELEGATE_CALL_TO_INVALID_CONTRACT, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a static call to invalid smart contract")
    public void staticCallToInvalidSmartContractEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, STATIC_CALL_TO_INVALID_CONTRACT, asAddress(RANDOM_ADDRESS));
        validateGasEstimation(data, STATIC_CALL_TO_INVALID_CONTRACT, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a call code to invalid smart contract")
    public void callCodeToInvalidSmartContractEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, CALL_CODE_TO_INVALID_CONTRACT, asAddress(RANDOM_ADDRESS));
        validateGasEstimation(data, CALL_CODE_TO_INVALID_CONTRACT, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes call to an external contract function")
    public void callCodeToExternalContractFunction() {
        var data = encodeData(
                ESTIMATE_GAS,
                CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION,
                new BigInteger("1"),
                asAddress(contractSolidityAddress));
        validateGasEstimation(data, CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes delegate call to an external contract function")
    public void delegateCallCodeToExternalContractFunction() {
        var data = encodeData(
                ESTIMATE_GAS,
                DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION,
                new BigInteger("1"),
                asAddress(contractSolidityAddress));
        validateGasEstimation(data, DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes call to an external contract view function")
    public void callCodeToExternalContractViewFunction() {
        var data = encodeData(
                ESTIMATE_GAS,
                CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW,
                new BigInteger("1"),
                asAddress(contractSolidityAddress));
        validateGasEstimation(data, CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a state update to a contract")
    public void stateUpdateContractFunction() {
        // making 5 times to state update
        var data = encodeData(ESTIMATE_GAS, STATE_UPDATE_OF_CONTRACT, new BigInteger("5"));
        validateGasEstimation(data, STATE_UPDATE_OF_CONTRACT, contractSolidityAddress);
    }

    @Then(
            "I call estimateGas with function that makes a state update to a contract several times and estimateGas is higher")
    public void progressiveStateUpdateContractFunction() {
        // making 5 times to state update
        var data = encodeData(ESTIMATE_GAS, STATE_UPDATE_OF_CONTRACT, new BigInteger("5"));
        var firstResponse = estimateContract(data, contractSolidityAddress)
                .getResultAsNumber()
                .intValue();
        // making 10 times to state update
        var secondData = encodeData(ESTIMATE_GAS, STATE_UPDATE_OF_CONTRACT, new BigInteger("10"));
        var secondResponse = estimateContract(secondData, contractSolidityAddress)
                .getResultAsNumber()
                .intValue();
        // verifying that estimateGas for 10 state updates is higher than 5 state updates
        assertTrue(secondResponse > firstResponse);
    }

    @Then("I call estimateGas with function that executes reentrancy attack with call")
    public void reentrancyCallAttackFunction() {
        var data = encodeData(
                ESTIMATE_GAS, REENTRANCY_CALL_ATTACK, asAddress(RANDOM_ADDRESS), new BigInteger("10000000000"));
        validateGasEstimation(data, REENTRANCY_CALL_ATTACK, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that executes gasLeft")
    public void getGasLeftContractFunction() {
        validateGasEstimation(encodeData(ESTIMATE_GAS, GET_GAS_LEFT), GET_GAS_LEFT, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that executes positive nested calls")
    public void positiveNestedCallsFunction() {
        var data = encodeData(
                ESTIMATE_GAS,
                NESTED_CALLS_POSITIVE,
                new BigInteger("1"),
                new BigInteger("10"),
                asAddress(contractSolidityAddress));
        validateGasEstimation(data, NESTED_CALLS_POSITIVE, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that executes limited nested calls")
    public void limitedNestedCallsFunction() {
        // verify that after exceeding a number of nested calls that the estimated gas would return the same
        // we will execute with 500, 1024 and 1025, and it should return the same estimatedGas
        var data = encodeData(
                ESTIMATE_GAS,
                NESTED_CALLS_LIMITED,
                new BigInteger("1"),
                new BigInteger("500"),
                asAddress(contractSolidityAddress));
        validateGasEstimation(data, NESTED_CALLS_LIMITED, contractSolidityAddress);
        var secondData = encodeData(
                ESTIMATE_GAS,
                NESTED_CALLS_LIMITED,
                new BigInteger("1"),
                new BigInteger("1024"),
                asAddress(contractSolidityAddress));
        validateGasEstimation(secondData, NESTED_CALLS_LIMITED, contractSolidityAddress);
        var thirdData = encodeData(
                ESTIMATE_GAS,
                NESTED_CALLS_LIMITED,
                new BigInteger("1"),
                new BigInteger("1025"),
                asAddress(contractSolidityAddress));
        validateGasEstimation(thirdData, NESTED_CALLS_LIMITED, contractSolidityAddress);
    }

    @Then("I call estimateGas with IERC20 token transfer using long zero address as receiver")
    public void ierc20TransferWithLongZeroAddressForReceiver() {
        var data = encodeData(IERC20_TOKEN_TRANSFER, asAddress(receiverAccountId), new BigInteger("1"));
        validateGasEstimation(data, IERC20_TOKEN_TRANSFER, fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with IERC20 token transfer using evm address as receiver")
    public void ierc20TransferWithEvmAddressForReceiver() {
        var accountInfo = mirrorClient.getAccountDetailsByAccountId(receiverAccountId.getAccountId());
        var data = encodeData(
                IERC20_TOKEN_TRANSFER, asAddress(accountInfo.getEvmAddress().replace("0x", "")), new BigInteger("1"));
        validateGasEstimation(data, IERC20_TOKEN_TRANSFER, fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with IERC20 token approve using evm address as receiver")
    public void ierc20ApproveWithEvmAddressForReceiver() {
        var accountInfo = mirrorClient.getAccountDetailsByAccountId(receiverAccountId.getAccountId());
        var data = encodeData(
                IERC20_TOKEN_APPROVE, asAddress(accountInfo.getEvmAddress().replace("0x", "")), new BigInteger("1"));
        validateGasEstimation(data, IERC20_TOKEN_APPROVE, fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with IERC20 token associate using evm address as receiver")
    public void ierc20AssociateWithEvmAddressForReceiver() {
        validateGasEstimation(
                encodeData(IERC20_TOKEN_ASSOCIATE), IERC20_TOKEN_ASSOCIATE, fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with IERC20 token dissociate using evm address as receiver")
    public void ierc20DissociateWithEvmAddressForReceiver() {
        validateGasEstimation(
                encodeData(IERC20_TOKEN_DISSOCIATE), IERC20_TOKEN_DISSOCIATE, fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with contract deploy with bytecode as data")
    public void contractDeployEstimateGas() {
        var bytecodeData = deployedContract.compiledSolidityArtifact().getBytecode();
        validateGasEstimation(bytecodeData, DEPLOY_CONTRACT_VIA_BYTECODE_DATA, null);
    }

    @Then("I call estimateGas with contract deploy with bytecode as data with sender")
    public void contractDeployEstimateGasWithSender() {
        var bytecodeData = deployedContract.compiledSolidityArtifact().getBytecode();
        validateGasEstimation(
                bytecodeData, DEPLOY_CONTRACT_VIA_BYTECODE_DATA, null, Optional.of(contractClient.getClientAddress()));
    }

    @Then("I call estimateGas with contract deploy with bytecode as data with invalid sender")
    public void contractDeployEstimateGasWithInvalidSender() {
        var bytecodeData = deployedContract.compiledSolidityArtifact().getBytecode();
        validateGasEstimation(
                bytecodeData,
                DEPLOY_CONTRACT_VIA_BYTECODE_DATA,
                null,
                Optional.of("0x0000000000000000000000000000000000000167"));
    }

    /**
     * Estimate gas values are hardcoded at this moment until we get better solution such as actual gas used returned
     * from the consensus node. It will be changed in future PR when actualGasUsed field is added to the protobufs.
     */
    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements ContractMethodInterface {
        ADDRESS_BALANCE("addressBalance", 24041),
        CALL_CODE_TO_CONTRACT("callCodeToContract", 25128),
        CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION("callExternalFunctionNTimes", 26100),
        CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW("delegatecallExternalViewFunctionNTimes", 22272),
        CALL_CODE_TO_INVALID_CONTRACT("callCodeToInvalidContract", 24031),
        CALL_TO_INVALID_CONTRACT("callToInvalidContract", 24374),
        DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION("delegatecallExternalFunctionNTimes", 24712),
        DELEGATE_CALL_TO_CONTRACT("delegateCallToContract", 25124),
        DELEGATE_CALL_TO_INVALID_CONTRACT("delegateCallToInvalidContract", 24350),
        DEPLOY_CONTRACT_VIA_CREATE_OPCODE("deployViaCreate", 53477),
        DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE("deployViaCreate2", 55693),
        DEPLOY_CONTRACT_VIA_BYTECODE_DATA("", 254007),
        DESTROY("destroy", 26171),
        GET_GAS_LEFT("getGasLeft", 21326),
        GET_MOCK_ADDRESS("getMockContractAddress", 0),
        LOGS("logs", 28757),
        MESSAGE_SENDER("msgSender", 21290),
        MESSAGE_SIGNER("msgSig", 21252),
        MESSAGE_VALUE("msgValue", 21234),
        MULTIPLY_NUMBERS("pureMultiply", 21227),
        NESTED_CALLS_LIMITED("nestedCalls", 550093),
        NESTED_CALLS_POSITIVE("nestedCalls", 35975),
        REENTRANCY_CALL_ATTACK("reentrancyWithCall", 55818),
        STATIC_CALL_TO_CONTRACT("staticCallToContract", 25146),
        STATIC_CALL_TO_INVALID_CONTRACT("staticCallToInvalidContract", 24394),
        STATE_UPDATE_OF_CONTRACT("updateStateNTimes", 30500),
        TX_ORIGIN("txOrigin", 21289),
        UPDATE_COUNTER("updateCounter", 26335),
        WRONG_METHOD_SIGNATURE("ffffffff()", 0),
        IERC20_TOKEN_TRANSFER("transfer(address,uint256)", 37837),
        IERC20_TOKEN_APPROVE("approve(address,uint256)", 727978),
        IERC20_TOKEN_ASSOCIATE("associate()", 727972),
        IERC20_TOKEN_DISSOCIATE("dissociate()", 727972);

        private final String selector;
        private final int actualGas;
    }
}
