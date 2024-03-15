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
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.ADDRESS_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_CODE_TO_INVALID_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.CALL_TO_INVALID_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_TO_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DELEGATE_CALL_TO_INVALID_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_AND_CALL_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_AND_DESTROY;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_BYTECODE_DATA;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_OPCODE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DEPLOY_NEW_INSTANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.DESTROY;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.GET_GAS_LEFT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.GET_MOCK_ADDRESS;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_APPROVE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_ASSOCIATE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_DISSOCIATE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.IERC20_TOKEN_TRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.INCREMENT_COUNTER;
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
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.UPDATE_TYPE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimateFeature.ContractMethods.WRONG_METHOD_SIGNATURE;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.IS_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.rest.model.ContractAction;
import com.hedera.mirror.rest.model.ContractActionsResponse;
import com.hedera.mirror.rest.model.ContractResult;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.ExecuteContractResult;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.util.ModelBuilder;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;

@CustomLog
@RequiredArgsConstructor
public class EstimateFeature extends AbstractEstimateFeature {
    private static final String HEX_DIGITS = "0123456789abcdef";
    private static final String RANDOM_ADDRESS = to32BytesString(RandomStringUtils.random(40, HEX_DIGITS));
    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private static final int BASE_GAS_FEE = 21_000;
    private static final int ADDITIONAL_FEE_FOR_CREATE = 32_000;
    private DeployedContract deployedContract;
    private DeployedContract deployedPrecompileContract;
    private String contractSolidityAddress;
    private String precompileSolidityAddress;
    private String mockAddress;
    byte[] addressSelector;
    private TokenId fungibleTokenId;
    private String newAccountEvmAddress;
    private ExpandedAccountId receiverAccountId;

    @Given("I successfully create EstimateGas contract from contract bytes")
    public void createNewEstimateContract() {
        deployedContract = getContract(ESTIMATE_GAS);
        contractSolidityAddress = deployedContract.contractId().toSolidityAddress();
        newAccountEvmAddress =
                PrivateKey.generateECDSA().getPublicKey().toEvmAddress().toString();
        receiverAccountId = accountClient.getAccount(AccountNameEnum.BOB);
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

    @Given("I successfully create Precompile contract from contract bytes")
    public void createNewPrecompileContract() {
        deployedPrecompileContract = getContract(PRECOMPILE);
        precompileSolidityAddress = deployedPrecompileContract.contractId().toSolidityAddress();
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
        var data = encodeData(ESTIMATE_GAS, ADDRESS_BALANCE, asAddress(contractSolidityAddress));
        validateGasEstimation(data, ADDRESS_BALANCE, contractSolidityAddress);
    }

    @Then("I call estimateGas with function that changes contract slot information"
            + " by updating global contract field with the passed argument")
    public void updateCounterEstimateCall() {
        var data = encodeData(ESTIMATE_GAS, UPDATE_COUNTER, new BigInteger("100"));
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
        assertContractCallReturnsBadRequest(encodeData(WRONG_METHOD_SIGNATURE), mockAddress);
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

    @Then("I execute contractCall for function that changes the contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumed() {
        var data = encodeDataToByteArray(ESTIMATE_GAS, UPDATE_COUNTER, new BigInteger("5"));
        var txId = executeContractTransaction(deployedContract, UPDATE_COUNTER, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall with delegatecall and function that changes contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumedViaDelegateCall() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, INCREMENT_COUNTER);
        var data = encodeDataToByteArray(
                ESTIMATE_GAS, DELEGATE_CALL_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, DELEGATE_CALL_TO_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then(
            "I execute contractCall with delegatecall with low gas and function that changes contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumedViaDelegateCallWithLowGas() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, INCREMENT_COUNTER);
        var data = encodeDataToByteArray(
                ESTIMATE_GAS, DELEGATE_CALL_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, 21500L, DELEGATE_CALL_TO_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall with callcode and function that changes contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumedViaCallCode() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, INCREMENT_COUNTER);
        var data = encodeDataToByteArray(
                ESTIMATE_GAS, CALL_CODE_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, DELEGATE_CALL_TO_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then(
            "I execute contractCall with callcode with low gas and function that changes contract slot and verify gasConsumed")
    public void updateContractSlotGasConsumedViaCallCodeWithLowGas() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, INCREMENT_COUNTER);
        var data = encodeDataToByteArray(
                ESTIMATE_GAS, CALL_CODE_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, 21500L, DELEGATE_CALL_TO_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall with static call and verify gasConsumed")
    public void getAddressViaStaticCall() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, GET_MOCK_ADDRESS);
        var data = encodeDataToByteArray(
                ESTIMATE_GAS, STATIC_CALL_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, STATIC_CALL_TO_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall with static call with low gas and verify gasConsumed")
    public void getAddressViaStaticCallWithLowGas() {
        var selector = encodeDataToByteArray(ESTIMATE_GAS, GET_MOCK_ADDRESS);
        var data = encodeDataToByteArray(
                ESTIMATE_GAS, STATIC_CALL_TO_CONTRACT, asAddress(contractSolidityAddress), selector);
        var txId = executeContractTransaction(deployedContract, 21500L, STATIC_CALL_TO_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I trigger fallback function with transfer and verify gasConsumed")
    public void triggerFallbackAndTransferFundsToContract() {
        var data = encodeData(WRONG_METHOD_SIGNATURE).getBytes();
        var txId = executeContractTransaction(deployedContract, WRONG_METHOD_SIGNATURE, data, Hbar.fromTinybars(100));
        verifyGasConsumed(txId, data);
    }

    @Then("I trigger fallback function with send and verify gasConsumed")
    public void triggerFallbackAndSendFundsToContract() {
        var initialData = encodeDataToByteArray(ESTIMATE_GAS, UPDATE_TYPE, new BigInteger("2"));
        executeContractTransaction(deployedContract, UPDATE_TYPE, initialData, null);

        var data = encodeData(WRONG_METHOD_SIGNATURE).getBytes();
        var txId = executeContractTransaction(deployedContract, WRONG_METHOD_SIGNATURE, data, Hbar.fromTinybars(100));
        verifyGasConsumed(txId, data);
    }

    @Then("I trigger fallback function with call and verify gasConsumed")
    public void triggerFallWithCallToContract() {
        var initialData = encodeDataToByteArray(ESTIMATE_GAS, UPDATE_TYPE, new BigInteger("3"));
        executeContractTransaction(deployedContract, UPDATE_TYPE, initialData, null);

        var data = encodeData(WRONG_METHOD_SIGNATURE).getBytes();
        var txId = executeContractTransaction(deployedContract, WRONG_METHOD_SIGNATURE, data, Hbar.fromTinybars(100));
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall for nested function and verify gasConsumed")
    public void nestedCalls() {
        var data = encodeDataToByteArray(
                ESTIMATE_GAS,
                CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION,
                new BigInteger("1"),
                asAddress(contractSolidityAddress));
        var txId = executeContractTransaction(deployedContract, CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall for nested functions with lower gas limit and verify gasConsumed")
    public void manyNestedLowerGasLimitCalls() {
        var data = encodeDataToByteArray(
                ESTIMATE_GAS,
                CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION,
                new BigInteger("20"),
                asAddress(contractSolidityAddress));
        var txId = executeContractTransaction(deployedContract, 50000L, CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall for failing nested functions and verify gasConsumed")
    public void failingNestedFunction() {
        var data = encodeDataToByteArray(ESTIMATE_GAS, CALL_TO_INVALID_CONTRACT, asAddress(mockAddress));
        var txId = executeContractTransaction(deployedContract, CALL_TO_INVALID_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall for failing precompile function and verify gasConsumed")
    public void failingPrecompileFunction() {
        var data = encodeDataToByteArray(PRECOMPILE, IS_TOKEN_SELECTOR, asAddress(fungibleTokenId));
        var txId = executeContractTransaction(deployedPrecompileContract, 25400L, IS_TOKEN_SELECTOR, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall for contract deploy function via create and verify gasConsumed")
    public void deployContractViaCreate() {
        var data = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_CONTRACT_VIA_CREATE_OPCODE);
        var txId = executeContractTransaction(deployedContract, DEPLOY_CONTRACT_VIA_CREATE_OPCODE, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall for contract deploy function via create2 and verify gasConsumed")
    public void deployContractViaCreateTwo() {
        var data = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE);
        var txId = executeContractTransaction(deployedContract, DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute contractCall failing to deploy contract due to low gas and verify gasConsumed")
    public void failDeployComplexContract() {
        var data = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_NEW_INSTANCE);
        var txId = executeContractTransaction(deployedContract, 40000L, DEPLOY_NEW_INSTANCE, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute deploy and call contract and verify gasConsumed")
    public void deployAndCallContract() {
        var data = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_AND_CALL_CONTRACT, new BigInteger("5"));
        var txId = executeContractTransaction(deployedContract, DEPLOY_AND_CALL_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute deploy and call contract that fails and verify gasConsumed")
    public void deployAndCallFailContract() {
        var data = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_AND_CALL_CONTRACT, new BigInteger("11"));
        var txId = executeContractTransaction(deployedContract, DEPLOY_AND_CALL_CONTRACT, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute deploy and selfdestruct and verify gasConsumed")
    public void deployAndSelfDestructContract() {
        var data = encodeDataToByteArray(ESTIMATE_GAS, DEPLOY_AND_DESTROY);
        var txId = executeContractTransaction(deployedContract, DEPLOY_AND_DESTROY, data);
        verifyGasConsumed(txId, data);
    }

    @Then("I execute create operation with bad contract and verify gasConsumed")
    public void deployBadContract() throws DecoderException {
        var contractPath = "classpath:solidity/artifacts/contracts/EstimateGasContract.sol/DummyContract.json";
        var txId = createContractAndReturnTransactionId(contractPath);
        var initByteCode = Objects.requireNonNull(
                mirrorClient.getContractResultByTransactionId(txId).getFailedInitcode());
        verifyGasConsumed(txId, initByteCode);
    }

    @Then("I execute create operation with complex contract and verify gasConsumed")
    public void deployEstimateContract() throws DecoderException {
        var contractPath = ESTIMATE_GAS.getPath();
        var txId = createContractAndReturnTransactionId(contractPath);
        var contractId = Objects.requireNonNull(
                        mirrorClient.getTransactions(txId).getTransactions())
                .getFirst()
                .getEntityId();
        var successfulInitByteCode =
                Objects.requireNonNull(mirrorClient.getContractInfo(contractId).getBytecode());
        verifyGasConsumed(txId, successfulInitByteCode);
    }

    @Then("I execute create operation with complex contract and lower gas limit and verify gasConsumed")
    public void deployEstimateContractWithLowGas() throws DecoderException {
        var contractPath = ESTIMATE_GAS.getPath();
        var txId = createContractAndReturnTransactionId(contractPath, 320000L);
        var transactions =
                Objects.requireNonNull(mirrorClient.getTransactions(txId).getTransactions());
        var contractId = transactions.getFirst().getEntityId();
        var successfulInitByteCode =
                Objects.requireNonNull(mirrorClient.getContractInfo(contractId).getBytecode());
        verifyGasConsumed(txId, successfulInitByteCode);
    }

    private void verifyGasConsumed(String txId, byte[] data) {
        int intrinsicValue = calculateIntrinsicValue(data);
        verifyGasConsumedCommon(txId, BASE_GAS_FEE + intrinsicValue);
    }

    private void verifyGasConsumed(String txId, String bytecode) throws DecoderException {
        byte[] data = Hex.decodeHex(bytecode.replaceFirst("0x", ""));
        int intrinsicValue = calculateIntrinsicValue(data);
        verifyGasConsumedCommon(txId, BASE_GAS_FEE + ADDITIONAL_FEE_FOR_CREATE + intrinsicValue);
    }

    private void verifyGasConsumedCommon(String txId, int additionalGas) {
        var gasConsumed = getGasConsumedByTransactionId(txId);
        var gasUsed = getGasFromActions(txId);
        assertThat(gasConsumed).isEqualTo(gasUsed + additionalGas);
    }

    private String executeContractTransaction(
            DeployedContract deployedContract, SelectorInterface contractMethods, byte[] parameters) {

        return executeContractTransaction(
                deployedContract,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                contractMethods,
                parameters,
                null);
    }

    private String executeContractTransaction(
            DeployedContract deployedContract, SelectorInterface contractMethods, byte[] parameters, Hbar amount) {

        return executeContractTransaction(
                deployedContract,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                contractMethods,
                parameters,
                amount);
    }

    private String executeContractTransaction(
            DeployedContract deployedContract, Long gas, SelectorInterface contractMethods, byte[] parameters) {

        return executeContractTransaction(deployedContract, gas, contractMethods, parameters, null);
    }

    private String executeContractTransaction(
            DeployedContract deployedContract,
            Long gas,
            SelectorInterface contractMethods,
            byte[] parameters,
            Hbar amount) {
        try {
            ExecuteContractResult executeContractResult = contractClient.executeContract(
                    deployedContract.contractId(), gas, contractMethods.getSelector(), parameters, amount);
            networkTransactionResponse = executeContractResult.networkTransactionResponse();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
            return networkTransactionResponse.getTransactionIdStringNoCheckSum();
        } catch (Exception e) {
            return extractTransactionId(e.getMessage());
        }
    }

    private String createContractAndReturnTransactionId(String resourcePath, Long gas) {
        var resource = resourceLoader.getResource(resourcePath);
        try (var in = resource.getInputStream()) {
            CompiledSolidityArtifact compiledSolidityArtifact = readCompiledArtifact(in);
            var fileId =
                    persistContractBytes(compiledSolidityArtifact.getBytecode().replaceFirst("0x", ""));
            try {
                networkTransactionResponse = contractClient.createContract(fileId, gas, Hbar.fromTinybars(0), null);
                return networkTransactionResponse.getTransactionIdStringNoCheckSum();
            } catch (Exception e) {
                return extractTransactionId(e.getMessage());
            }
        } catch (IOException e) {
            log.warn("Exception: ", e);
            throw new RuntimeException(e);
        }
    }

    private String createContractAndReturnTransactionId(String resourcePath) {
        return createContractAndReturnTransactionId(
                resourcePath,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas());
    }

    private static String extractTransactionId(String message) {
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)@(\\d+)\\.(\\d+)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);
        } else {
            return "Not found";
        }
    }

    private Long getGasConsumedByTransactionId(String transactionId) {
        ContractResult contractResult = mirrorClient.getContractResultByTransactionId(transactionId);
        return contractResult.getGasConsumed();
    }

    private long getGasFromActions(String transactionId) {
        return Optional.ofNullable(mirrorClient.getContractResultActionsByTransactionId(transactionId))
                .map(ContractActionsResponse::getActions)
                .filter(actions -> !actions.isEmpty())
                .map(List::getFirst)
                .map(ContractAction::getGasUsed)
                .orElse(0L); // Provide a default value in case any step results in null
    }

    /**
     * Calculates the intrinsic value by adding 4 for each 0 value and 16 for non-zero values.
     */
    public static int calculateIntrinsicValue(byte[] values) {
        int total = 0;
        for (byte value : values) {
            total += (value == 0) ? 4 : 16;
        }
        return total;
    }

    @RetryAsserts
    @Given("I verify the estimate contract bytecode is deployed")
    public void verifyEstimateContractIsDeployed() {
        verifyContractDeployed(contractSolidityAddress);
    }

    private void verifyContractDeployed(String contractAddress) {
        var response = mirrorClient.getContractInfo(contractAddress);
        Assertions.assertThat(response.getBytecode()).isNotBlank();
        Assertions.assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    /**
     * Estimate gas values are hardcoded at this moment until we get better solution such as actual gas used returned
     * from the consensus node. It will be changed in future PR when actualGasUsed field is added to the protobufs.
     */
    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements ContractMethodInterface {
        ADDRESS_BALANCE("addressBalance", 21735),
        CALL_CODE_TO_CONTRACT("callCodeToContract", 25128),
        CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION("callExternalFunctionNTimes", 26100),
        CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION_VIEW("delegatecallExternalViewFunctionNTimes", 29809),
        CALL_CODE_TO_INVALID_CONTRACT("callCodeToInvalidContract", 24486),
        CALL_TO_INVALID_CONTRACT("callToInvalidContract", 24807),
        DELEGATE_CALL_CODE_TO_EXTERNAL_CONTRACT_FUNCTION("delegatecallExternalFunctionNTimes", 24712),
        DELEGATE_CALL_TO_CONTRACT("delegateCallToContract", 25124),
        DELEGATE_CALL_TO_INVALID_CONTRACT("delegateCallToInvalidContract", 24803),
        DEPLOY_CONTRACT_VIA_CREATE_OPCODE("deployViaCreate", 53631),
        DEPLOY_CONTRACT_VIA_CREATE_TWO_OPCODE("deployViaCreate2", 55786),
        DEPLOY_CONTRACT_VIA_BYTECODE_DATA("", 318113),
        DEPLOY_NEW_INSTANCE("createClone", 0), // Set actual gas to 0; unnecessary for gasConsumed test validation.
        DEPLOY_AND_CALL_CONTRACT("deployAndCallMockContract", 0),
        DEPLOY_AND_DESTROY("deployDestroy", 0), // Set actual gas to 0; unnecessary for gasConsumed test validation.
        DESTROY("destroy", 26300),
        GET_GAS_LEFT("getGasLeft", 21313),
        GET_MOCK_ADDRESS("getMockContractAddress", 0),
        INCREMENT_COUNTER("incrementCounter", 0), // Set actual gas to 0; unnecessary for gasConsumed test validation.
        LOGS("logs", 28822),
        MESSAGE_SENDER("msgSender", 21365),
        MESSAGE_SIGNER("msgSig", 21361),
        MESSAGE_VALUE("msgValue", 21265),
        MULTIPLY_NUMBERS("pureMultiply", 21281),
        NESTED_CALLS_LIMITED("nestedCalls", 544470),
        NESTED_CALLS_POSITIVE("nestedCalls", 35975),
        REENTRANCY_CALL_ATTACK("reentrancyWithCall", 56426),
        STATIC_CALL_TO_CONTRACT("staticCallToContract", 25146),
        STATIC_CALL_TO_INVALID_CONTRACT("staticCallToInvalidContract", 24826),
        STATE_UPDATE_OF_CONTRACT("updateStateNTimes", 28847),
        TX_ORIGIN("txOrigin", 21342),
        UPDATE_COUNTER("updateCounter", 26583),
        UPDATE_TYPE("updateType", 0), // Set actual gas to 0; unnecessary for gasConsumed test validation.
        WRONG_METHOD_SIGNATURE("ffffffff()", 0),
        IERC20_TOKEN_TRANSFER("transfer(address,uint256)", 38193),
        IERC20_TOKEN_APPROVE("approve(address,uint256)", 728550),
        IERC20_TOKEN_ASSOCIATE("associate()", 728033),
        IERC20_TOKEN_DISSOCIATE("dissociate()", 728033);

        private final String selector;
        private final int actualGas;
    }
}
