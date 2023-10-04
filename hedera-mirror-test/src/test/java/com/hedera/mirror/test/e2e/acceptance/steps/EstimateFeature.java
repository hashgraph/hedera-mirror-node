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

import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EstimateFeature extends AbstractEstimateFeature {
    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final String RANDOM_ADDRESS = to32BytesString(RandomStringUtils.random(40, HEX_DIGITS));
    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private DeployedContract deployedContract;
    private String contractSolidityAddress;
    private String mockAddress;
    byte[] getAddressSelectorByteArray;
    private TokenId fungibleTokenId;
    private String newAccountEvmAddress;
    private ExpandedAccountId receiverAccountId;

    @Given("I successfully create EstimateGas contract from contract bytes")
    public void createNewEstimateContract() throws IOException {
        deployedContract = getContract(ContractResource.ESTIMATE_GAS_TEST_CONTRACT);
        contractSolidityAddress = deployedContract.contractId().toSolidityAddress();
        newAccountEvmAddress =
                PrivateKey.generateECDSA().getPublicKey().toEvmAddress().toString();
        receiverAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
    }

    @Given("I successfully create fungible token")
    public void createFungibleToken() {
        fungibleTokenId = tokenClient.getToken(FUNGIBLE).tokenId();
    }

    @And("lower deviation is {int}% and upper deviation is {int}%")
    public void setDeviations(int lower, int upper) {
        lowerDeviation = lower;
        upperDeviation = upper;
    }

    @Then("I call estimateGas without arguments that multiplies two numbers")
    public void multiplyEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.MULTIPLY_SIMPLE_NUMBERS, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()),
                ContractMethods.MULTIPLY_SIMPLE_NUMBERS.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function msgSender")
    public void msgSenderEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.MESSAGE_SENDER, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()),
                ContractMethods.MESSAGE_SENDER.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function tx origin")
    public void txOriginEstimateCall() {
        validateGasEstimation(
                Strings.encode(
                        getFunctionFromArtifact(ContractMethods.TX_ORIGIN, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                                .encodeCallWithArgs()),
                ContractMethods.TX_ORIGIN.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function messageValue")
    public void msgValueEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.MESSAGE_VALUE, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()),
                ContractMethods.MESSAGE_VALUE.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function messageSigner")
    public void msgSignerEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.MESSAGE_SIGNER, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()),
                ContractMethods.MESSAGE_SIGNER.getActualGas(),
                contractSolidityAddress);
    }

    @RetryAsserts
    @Then("I call estimateGas with function balance of address")
    public void addressBalanceEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.ADDRESS_BALANCE, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(RANDOM_ADDRESS))),
                ContractMethods.ADDRESS_BALANCE.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that changes contract slot information"
            + " by updating global contract field with the passed argument")
    public void updateCounterEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.UPDATE_COUNTER, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(new BigInteger("5"))),
                ContractMethods.UPDATE_COUNTER.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that successfully deploys a new smart contract via CREATE op code")
    public void deployContractViaCreateOpcodeEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_OPCODE,
                                ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()),
                ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_OPCODE.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that successfully deploys a new smart contract via CREATE2 op code")
    public void deployContractViaCreateTwoOpcodeEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE,
                                ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()),
                ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I get mock contract address and getAddress selector")
    public void getMockAddress() {
        var contractCallGetMockAddress = ContractCallRequest.builder()
                .data(Strings.encode(getFunctionFromArtifact(
                                ContractMethods.GET_MOCK_ADDRESS, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()))
                .to(contractSolidityAddress)
                .estimate(false)
                .build();
        mockAddress = mirrorClient.contractsCall(contractCallGetMockAddress).getResultAsAddress();
        getAddressSelectorByteArray = new BigInteger("0x38cc4831".substring(2), 16).toByteArray();
    }

    @Then("I call estimateGas with function that makes a static call to a method from a different contract")
    public void staticCallToContractEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.STATIC_CALL_TO_CONTRACT, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(mockAddress), getAddressSelectorByteArray)),
                ContractMethods.STATIC_CALL_TO_CONTRACT.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a delegate call to a method from a different contract")
    public void delegateCallToContractEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.DELEGATE_CALL_TO_CONTRACT, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(mockAddress), getAddressSelectorByteArray)),
                ContractMethods.DELEGATE_CALL_TO_CONTRACT.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a call code to a method from a different contract")
    public void callCodeToContractEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.CALL_CODE_TO_CONTRACT, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(mockAddress), getAddressSelectorByteArray)),
                ContractMethods.CALL_CODE_TO_CONTRACT.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that performs LOG0, LOG1, LOG2, LOG3, LOG4 operations")
    public void logsEstimateCall() {
        validateGasEstimation(
                Strings.encode(
                        getFunctionFromArtifact(ContractMethods.LOGS, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                                .encodeCallWithArgs()),
                ContractMethods.LOGS.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that performs self destruct")
    public void destroyEstimateCall() {
        validateGasEstimation(
                Strings.encode(
                        getFunctionFromArtifact(ContractMethods.DESTROY, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                                .encodeCallWithArgs()),
                ContractMethods.DESTROY.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with request body that contains wrong method signature")
    public void wrongMethodSignatureEstimateCall() {
        assertContractCallReturnsBadRequest(
                new Function(ContractMethods.WRONG_METHOD_SIGNATURE.getSelector()).encodeCallWithArgs(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with wrong encoded parameter")
    public void wrongEncodedParameterEstimateCall() {
        // wrong encoded address -> it should contain leading zero's equal to 64 characters
        String wrongEncodedAddress = "5642";
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ContractMethods.ADDRESS_BALANCE.getSelector() + wrongEncodedAddress)
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
                .data(Strings.encode(getFunctionFromArtifact(
                                ContractMethods.MESSAGE_SIGNER, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()))
                .to(contractSolidityAddress)
                .from(newAccountEvmAddress)
                .estimate(true)
                .build();
        ContractCallResponse msgSignerResponse = mirrorClient.contractsCall(contractCallRequestBody);
        int estimatedGas = msgSignerResponse.getResultAsNumber().intValue();

        assertTrue(isWithinDeviation(
                ContractMethods.MESSAGE_SIGNER.getActualGas(), estimatedGas, lowerDeviation, upperDeviation));
    }

    @Then("I call estimateGas with function that makes a call to invalid smart contract")
    public void callToInvalidSmartContractEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.CALL_TO_INVALID_CONTRACT, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(RANDOM_ADDRESS))),
                ContractMethods.CALL_TO_INVALID_CONTRACT.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a delegate call to invalid smart contract")
    public void delegateCallToInvalidSmartContractEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.DELEGATE_CALL_TO_INVALID_CONTRACT,
                                ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(RANDOM_ADDRESS))),
                ContractMethods.DELEGATE_CALL_TO_INVALID_CONTRACT.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a static call to invalid smart contract")
    public void staticCallToInvalidSmartContractEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.STATIC_CALL_TO_INVALID_CONTRACT,
                                ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(RANDOM_ADDRESS))),
                ContractMethods.STATIC_CALL_TO_INVALID_CONTRACT.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a call code to invalid smart contract")
    public void callCodeToInvalidSmartContractEstimateCall() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.CALL_CODE_TO_INVALID_CONTRACT,
                                ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(RANDOM_ADDRESS))),
                ContractMethods.CALL_CODE_TO_INVALID_CONTRACT.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes call to an external contract function")
    public void callCodeToExternalContractFunction() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION,
                                ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(new BigInteger("1"), asAddress(contractSolidityAddress))),
                ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes delegate call to an external contract function")
    public void delegateCallCodeToExternalContractFunction() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION,
                                ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(new BigInteger("1"), asAddress(contractSolidityAddress))),
                ContractMethods.DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes call to an external contract view function")
    public void callCodeToExternalContractViewFunction() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW,
                                ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(new BigInteger("1"), asAddress(contractSolidityAddress))),
                ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that makes a state update to a contract")
    public void stateUpdateContractFunction() {
        // making 5 times to state update
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.STATE_UPDATE_OF_CONTRACT, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(new BigInteger("5"))),
                ContractMethods.STATE_UPDATE_OF_CONTRACT.getActualGas(),
                contractSolidityAddress);
    }

    @Then(
            "I call estimateGas with function that makes a state update to a contract several times and estimateGas is higher")
    public void progressiveStateUpdateContractFunction() {
        // making 5 times to state update
        var contractCallRequestStateUpdateWithFive = ContractCallRequest.builder()
                .data(Strings.encode(getFunctionFromArtifact(
                                ContractMethods.STATE_UPDATE_OF_CONTRACT, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(new BigInteger("5"))))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse fiveStateUpdatesResponse =
                mirrorClient.contractsCall(contractCallRequestStateUpdateWithFive);
        int estimatedGasOfFiveStateUpdates =
                fiveStateUpdatesResponse.getResultAsNumber().intValue();
        // making 10 times to state update
        var contractCallRequestStateUpdateWithTen = ContractCallRequest.builder()
                .data(Strings.encode(getFunctionFromArtifact(
                                ContractMethods.STATE_UPDATE_OF_CONTRACT, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(new BigInteger("10"))))
                .to(contractSolidityAddress)
                .estimate(true)
                .build();
        ContractCallResponse tenStateUpdatesResponse =
                mirrorClient.contractsCall(contractCallRequestStateUpdateWithTen);
        int estimatedGasOfTenStateUpdates =
                tenStateUpdatesResponse.getResultAsNumber().intValue();
        // verifying that estimateGas for 10 state updates is higher than 5 state updates
        assertTrue(estimatedGasOfTenStateUpdates > estimatedGasOfFiveStateUpdates);
    }

    @Then("I call estimateGas with function that executes reentrancy attack with call")
    public void reentrancyCallAttackFunction() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.REENTRANCY_CALL_ATTACK, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(asAddress(RANDOM_ADDRESS), new BigInteger("10000000000"))),
                ContractMethods.REENTRANCY_CALL_ATTACK.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that executes gasLeft")
    public void getGasLeftContractFunction() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.GET_GAS_LEFT, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs()),
                ContractMethods.GET_GAS_LEFT.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that executes positive nested calls")
    public void positiveNestedCallsFunction() {
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.NESTED_CALLS_POSITIVE, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(
                                new BigInteger("1"), new BigInteger("10"), asAddress(contractSolidityAddress))),
                ContractMethods.NESTED_CALLS_POSITIVE.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with function that executes limited nested calls")
    public void limitedNestedCallsFunction() {
        // verify that after exceeding a number of nested calls that the estimated gas would return the same
        // we will execute with 500, 1024 and 1025, and it should return the same estimatedGas
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.NESTED_CALLS_LIMITED, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(
                                new BigInteger("1"), new BigInteger("500"), asAddress(contractSolidityAddress))),
                ContractMethods.NESTED_CALLS_LIMITED.getActualGas(),
                contractSolidityAddress);
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.NESTED_CALLS_LIMITED, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(
                                new BigInteger("1"), new BigInteger("1024"), asAddress(contractSolidityAddress))),
                ContractMethods.NESTED_CALLS_LIMITED.getActualGas(),
                contractSolidityAddress);
        validateGasEstimation(
                Strings.encode(getFunctionFromArtifact(
                                ContractMethods.NESTED_CALLS_LIMITED, ContractResource.ESTIMATE_GAS_TEST_CONTRACT)
                        .encodeCallWithArgs(
                                new BigInteger("1"), new BigInteger("1025"), asAddress(contractSolidityAddress))),
                ContractMethods.NESTED_CALLS_LIMITED.getActualGas(),
                contractSolidityAddress);
    }

    @Then("I call estimateGas with IERC20 token transfer using long zero address as receiver")
    public void ierc20TransferWithLongZeroAddressForReceiver() {
        validateGasEstimation(
                Strings.encode(new Function(ContractMethods.IERC20_TOKEN_TRANSFER.getSelector())
                        .encodeCallWithArgs(
                                asAddress(receiverAccountId.getAccountId().toSolidityAddress()), new BigInteger("1"))),
                ContractMethods.IERC20_TOKEN_TRANSFER.getActualGas(),
                fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with IERC20 token transfer using evm address as receiver")
    public void ierc20TransferWithEvmAddressForReceiver() {
        var accountInfo = mirrorClient.getAccountDetailsByAccountId(receiverAccountId.getAccountId());
        validateGasEstimation(
                Strings.encode(new Function(ContractMethods.IERC20_TOKEN_TRANSFER.getSelector())
                        .encodeCallWithArgs(
                                asAddress(accountInfo.getEvmAddress().replace("0x", "")), new BigInteger("1"))),
                ContractMethods.IERC20_TOKEN_TRANSFER.getActualGas(),
                fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with IERC20 token approve using evm address as receiver")
    public void ierc20ApproveWithEvmAddressForReceiver() {
        var accountInfo = mirrorClient.getAccountDetailsByAccountId(receiverAccountId.getAccountId());
        validateGasEstimation(
                (Strings.encode(new Function(ContractMethods.IERC20_TOKEN_APPROVE.getSelector())
                        .encodeCallWithArgs(
                                asAddress(accountInfo.getEvmAddress().replace("0x", "")), new BigInteger("1")))),
                ContractMethods.IERC20_TOKEN_APPROVE.getActualGas(),
                fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with IERC20 token associate using evm address as receiver")
    public void ierc20AssociateWithEvmAddressForReceiver() {
        validateGasEstimation(
                Strings.encode(new Function(ContractMethods.IERC20_TOKEN_ASSOCIATE.getSelector()).encodeCallWithArgs()),
                ContractMethods.IERC20_TOKEN_ASSOCIATE.getActualGas(),
                fungibleTokenId.toSolidityAddress());
    }

    @Then("I call estimateGas with IERC20 token dissociate using evm address as receiver")
    public void ierc20DissociateWithEvmAddressForReceiver() {
        validateGasEstimation(
                (Strings.encode(
                        new Function(ContractMethods.IERC20_TOKEN_DISSOCIATE.getSelector()).encodeCallWithArgs())),
                ContractMethods.IERC20_TOKEN_DISSOCIATE.getActualGas(),
                fungibleTokenId.toSolidityAddress());
    }

    /**
     * Estimate gas values are hardcoded at this moment until we get better solution such as actual gas used returned
     * from the consensus node. It will be changed in future PR when actualGasUsed field is added to the protobufs.
     */
    @Getter
    @RequiredArgsConstructor
    private enum ContractMethods implements ContractMethodInterface {
        ADDRESS_BALANCE("addressBalance", 24041),
        CALL_CODE_TO_CONTRACT("callCodeToContract", 26398),
        CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION("callExternalFunctionNTimes", 26100),
        CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW("delegatecallExternalViewFunctionNTimes", 22272),
        CALL_CODE_TO_INVALID_CONTRACT("callCodeToInvalidContract", 24031),
        CALL_TO_INVALID_CONTRACT("callToInvalidContract", 24374),
        DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION("delegatecallExternalFunctionNTimes", 24712),
        DELEGATE_CALL_TO_CONTRACT("delegateCallToContract", 26417),
        DELEGATE_CALL_TO_INVALID_CONTRACT("delegateCallToInvalidContract", 24350),
        DEPLOY_CONTRACT_VIA_CREATE_OPCODE("deployViaCreate", 53477),
        DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE("deployViaCreate2", 55693),
        DESTROY("destroy", 26171),
        GET_GAS_LEFT("getGasLeft", 21326),
        GET_MOCK_ADDRESS("getMockContractAddress", 0),
        LOGS("logs", 28757),
        MESSAGE_SENDER("msgSender", 21290),
        MESSAGE_SIGNER("msgSig", 21252),
        MESSAGE_VALUE("msgValue", 21234),
        MULTIPLY_SIMPLE_NUMBERS("pureMultiply", 21227),
        NESTED_CALLS_LIMITED("nestedCalls", 525255),
        NESTED_CALLS_POSITIVE("nestedCalls", 35975),
        REENTRANCY_CALL_ATTACK("reentrancyWithCall", 55818),
        STATIC_CALL_TO_CONTRACT("staticCallToContract", 26416),
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
