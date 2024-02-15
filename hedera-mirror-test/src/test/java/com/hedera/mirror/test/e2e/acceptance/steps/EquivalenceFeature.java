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
import static com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum.FUNGIBLE;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_CALL;
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.EQUIVALENCE_DESTRUCT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.COPY_CODE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.DESTROY_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_BALANCE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CODE_HASH;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CODE_SIZE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EquivalenceFeature.ContractMethods.GET_CURRENT_ADDRESS;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.TupleType;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.response.GeneralContractExecutionResponse;
import com.hedera.mirror.test.e2e.acceptance.util.TestUtil;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class EquivalenceFeature extends AbstractFeature {
    private final AcceptanceTestProperties acceptanceTestProperties;
    private static final String OBTAINER_SAME_CONTRACT_ID_EXCEPTION = "OBTAINER_SAME_CONTRACT_ID";
    private static final String INVALID_SOLIDITY_ADDRESS = "INVALID_SOLIDITY_ADDRESS";
    private static final String INVALID_FEE_SUBMITTED = "INVALID_FEE_SUBMITTED";
    private static final String BAD_REQUEST = "400 Bad Request";
    private static final String TRANSACTION_SUCCESSFUL_MESSAGE = "Transaction successful";
    private static final TupleType INTERNAL_CALL_RETURN_TYPE = TupleType.parse("(bool,bytes)");
    // tinycentsToTinybars(uint256)
    private static final int TO_TINYBARS_SELECTOR = 0x2e3cff6a;
    // random256BitGenerator(uint256)
    public static final int PSEUDORANDOM_SEED_GENERATOR_SELECTOR = 0xd83bf9a1;

    private DeployedContract equivalenceDestructContract;
    private DeployedContract equivalenceCallContract;

    private String equivalenceDestructContractSolidityAddress;
    private String equivalenceCallContractSolidityAddress;
    private TokenId fungibleTokenId;

    private final TokenClient tokenClient;

    @Given("I successfully create selfdestruct contract")
    public void createNewSelfDestructContract() {
        equivalenceDestructContract = getContract(EQUIVALENCE_DESTRUCT);
        equivalenceDestructContractSolidityAddress =
                equivalenceDestructContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create equivalence call contract")
    public void createNewEquivalenceCallContract() {
        equivalenceCallContract = getContract(EQUIVALENCE_CALL);
        equivalenceCallContractSolidityAddress =
                equivalenceCallContract.contractId().toSolidityAddress();
    }

    @RetryAsserts
    @Given("I verify the equivalence contract bytecode is deployed")
    public void verifyEquivalenceContractDeployed() {
        verifyContractDeployed(equivalenceCallContractSolidityAddress);
    }

    @RetryAsserts
    @Given("I verify the selfdestruct contract bytecode is deployed")
    public void verifyDestructContractDeployed() {
        verifyContractDeployed(equivalenceDestructContractSolidityAddress);
    }

    private void verifyContractDeployed(String contractAddress) {
        var response = mirrorClient.getContractInfo(contractAddress);
        Assertions.assertThat(response.getBytecode()).isNotBlank();
        Assertions.assertThat(response.getRuntimeBytecode()).isNotBlank();
    }

    @Given("I successfully create tokens")
    public void createTokens() {
        fungibleTokenId = tokenClient.getToken(FUNGIBLE).tokenId();
    }

    @And("I associate {token} to contract")
    public void associateTokenToContract(TokenNameEnum tokenName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        tokenClient.associate(equivalenceCallContract.contractId(), tokenId);
    }

    @Then("I execute selfdestruct and set beneficiary to {string} address")
    public void selfDestructAndSetBeneficiary(String beneficiary) {
        var nodeType = acceptanceTestProperties.getNodeType();
        var accountId = new AccountId(extractAccountNumber(beneficiary));

        var data = encodeData(EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, asAddress(accountId));
        var functionResult = callContract(
                nodeType, StringUtils.EMPTY, EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, data, TupleType.EMPTY);

        // The contract is removed from the map when executed against the consensus node to prevent potential issues
        // in subsequent runs. This is because if the contract were not removed, the system would seek the cached
        // address on the next execution.
        if (acceptanceTestProperties.getNodeType().equals(NodeNameEnum.CONSENSUS)) {
            removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        }

        final var message = functionResult.getResultAsText();

        if (extractAccountNumber(beneficiary) < 751) {
            var condition = message.startsWith("400 Bad Request") || message.equals(INVALID_SOLIDITY_ADDRESS);
            assertThat(condition).isTrue();
        } else {
            var condition = functionResult.getResult().equals("0x") || message.equals(TRANSACTION_SUCCESSFUL_MESSAGE);
            assertThat(condition).isTrue();
        }
    }

    @Then("I execute balance opcode to system account {string} address would return 0")
    public void balanceOfAddress(String address) {
        var nodeType = acceptanceTestProperties.getNodeType();
        final var accountId = new AccountId(extractAccountNumber(address));
        var data = encodeData(EQUIVALENCE_CALL, GET_BALANCE, asAddress(accountId));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_BALANCE, data, BIG_INTEGER_TUPLE);
        assertThat(BigInteger.ZERO).isEqualTo(functionResult.getResultAsNumber());
    }

    @Then("I execute balance opcode against a contract with balance")
    public void balanceOfContract() {
        var nodeType = acceptanceTestProperties.getNodeType();
        var data = encodeData(EQUIVALENCE_CALL, GET_BALANCE, asAddress(equivalenceDestructContract.contractId()));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_BALANCE, data, BIG_INTEGER_TUPLE);
        assertThat(new BigInteger("10000")).isEqualTo(functionResult.getResultAsNumber());
    }

    @Then("I verify extcodesize opcode against a system account {string} address returns 0")
    public void extCodeSizeAgainstSystemAccount(String address) {
        var nodeType = acceptanceTestProperties.getNodeType();
        final var accountId = new AccountId(extractAccountNumber(address));
        var data = encodeData(EQUIVALENCE_CALL, GET_CODE_SIZE, asAddress(accountId));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_CODE_SIZE, data, BIG_INTEGER_TUPLE);
        assertThat(BigInteger.ZERO).isEqualTo(functionResult.getResultAsNumber());
    }

    @Then("I verify extcodecopy opcode against a system account {string} address returns empty bytes")
    public void extCodeCopyAgainstSystemAccount(String address) {
        var nodeType = acceptanceTestProperties.getNodeType();
        final var accountId = new AccountId(extractAccountNumber(address));
        var data = encodeData(EQUIVALENCE_CALL, COPY_CODE, asAddress(accountId));
        var functionResult = callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, COPY_CODE, data, BYTES_TUPLE);
        assertThat("").isEqualTo(functionResult.getResultAsText());
    }

    @Then("I verify extcodehash opcode against a system account {string} address returns empty bytes")
    public void extCodeHashAgainstSystemAccount(String address) {
        var nodeType = acceptanceTestProperties.getNodeType();
        final var accountId = new AccountId(extractAccountNumber(address));
        var data = encodeData(EQUIVALENCE_CALL, GET_CODE_HASH, asAddress(accountId));
        var functionResult =
                callContract(nodeType, StringUtils.EMPTY, EQUIVALENCE_CALL, GET_CODE_HASH, data, BYTES_TUPLE);
        assertThat(new byte[0]).isEqualTo(functionResult.getResultAsBytes().toArray());
    }

    @Then("I execute selfdestruct and set beneficiary to the deleted contract address")
    public void selfDestructAndSetBeneficiaryToDeletedContract() {
        var nodeType = acceptanceTestProperties.getNodeType();
        var data = encodeData(
                EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, asAddress(equivalenceDestructContractSolidityAddress));
        var functionResult = callContract(
                nodeType, StringUtils.EMPTY, EQUIVALENCE_DESTRUCT, DESTROY_CONTRACT, data, TupleType.EMPTY);
        if (nodeType.equals(NodeNameEnum.CONSENSUS)) {
            removeFromContractIdMap(EQUIVALENCE_DESTRUCT);
        }
        var message = functionResult.getResultAsText();
        var condition = message.startsWith(BAD_REQUEST) || message.equals(OBTAINER_SAME_CONTRACT_ID_EXCEPTION);
        assertThat(condition).isTrue();
    }

    @Given("I successfully create fungible token for internal calls tests")
    public void createFungibleToken() {
        var tokenResponse = tokenClient.getToken(FUNGIBLE);
        if (tokenResponse.response() != null) {
            fungibleTokenId = tokenResponse.tokenId();
            networkTransactionResponse = tokenResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
    }

    @Then("I make internal {string} to system account {string} {string} amount")
    public void callToSystemAddress(String call, String address, String amountType) {
        var accountNumber = extractAccountNumber(address);
        var accountId = new AccountId(accountNumber).toSolidityAddress();
        var callType = getMethodName(call, amountType);
        var node = acceptanceTestProperties.getNodeType();

        byte[] functionParameterData =
                constructFunctionParameterDataForInternalCallToSystemAccount(accountNumber, call, callType);
        byte[] data;
        if (functionParameterData == null) {
            callType = getMethodNameForHtsAccount(call, amountType);
            data = encodeDataToByteArray(EQUIVALENCE_CALL, callType, TestUtil.asAddress(fungibleTokenId));
        } else {
            data = encodeDataToByteArray(
                    EQUIVALENCE_CALL, callType, TestUtil.asAddress(accountId), functionParameterData);
        }

        Hbar amount = null;
        if (amountType.equals("with")) {
            amount = Hbar.fromTinybars(10);
        }

        var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, amount);
        validateResponseFromCallToSystemAccount(accountNumber, call, amount, functionParameterData, response);
    }

    @Then("I execute internal {string} against {string} contract {string} amount")
    public void executeInternalCallAgainstContract(String call, String payable, String amountType) {
        var callType = getMethodName(call, amountType);
        var node = acceptanceTestProperties.getNodeType();

        DeployedContract receiverContract;
        final var isPayable = payable.equals("payable");
        if (isPayable) {
            receiverContract = equivalenceDestructContract;
        } else {
            receiverContract = equivalenceCallContract;
        }

        if (amountType.equals("with")) {
            final var data = encodeDataToByteArray(
                    EQUIVALENCE_CALL,
                    "makeCallWithAmountRevert",
                    TestUtil.asAddress(receiverContract.contractId().toSolidityAddress()),
                    new byte[0]);
            GeneralContractExecutionResponse response;
            try {
                response = callContract(
                        node,
                        StringUtils.EMPTY,
                        EQUIVALENCE_CALL,
                        "makeCallWithAmountRevert",
                        data,
                        Hbar.fromTinybars(123L));
                if (isPayable) {
                    assertThat(response.errorMessage()).isBlank();
                }
            } catch (Exception e) {
                if (!isPayable) {
                    assertThat(e.getMessage()).contains(BAD_REQUEST);
                }
            }
        } else {
            byte[] data = encodeDataToByteArray(
                    EQUIVALENCE_CALL,
                    callType,
                    TestUtil.asAddress(receiverContract.contractId().toSolidityAddress()),
                    new byte[0]);
            final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);
            assertThat(response.errorMessage()).isBlank();
        }
    }

    @Then("I execute internal {string} against Identity precompile")
    public void executeAllCallsForIdentity(String calltype) {
        var message = "Encode me!";
        var messageBytes = message.getBytes(StandardCharsets.UTF_8);
        var callType = getMethodName(calltype, "without");
        var node = acceptanceTestProperties.getNodeType();
        var data = encodeDataToByteArray(
                EQUIVALENCE_CALL,
                callType,
                TestUtil.asAddress("0x0000000000000000000000000000000000000004"),
                messageBytes);

        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);

        var decodedResult = getDecodedInternalCallResult(response);
        assertEquals(message, new String(decodedResult, StandardCharsets.UTF_8));
    }

    @Then("I execute internal {string} against Ecrecover precompile")
    public void executeAllCallsForEcrecover(String calltype) {
        var messageSignerAddress = "0x05FbA803Be258049A27B820088bab1cAD2058871";
        var hashedMessage =
                "0xf950ac8b7f08b2f5ffa0f893d0f85398135301759b768dc20c1e16d9cdba5b53000000000000000000000000000000000000000000000000000000000000001b45e5f9dc145b79479820a9dfa925bb698333e7f17b7d570391e8487c96a39e07675b682b2519f6232152a9f6f4f5923d171dfb7636daceee2c776edecc6c8b64";
        var callType = getMethodName(calltype, "without");
        var node = acceptanceTestProperties.getNodeType();
        var data = encodeDataToByteArray(
                EQUIVALENCE_CALL,
                callType,
                TestUtil.asAddress("0x0000000000000000000000000000000000000001"),
                Bytes.fromHexString(hashedMessage).toArrayUnsafe());
        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);

        var decodedResult = getDecodedInternalCallResult(response);
        assertEquals(messageSignerAddress, asAddress(decodedResult).toString());
    }

    @Then("I execute internal {string} against SHA-256 precompile")
    public void executeAllCallsForSha256(String calltype) {
        var message = "Encode me!";
        var hashedMessage = "68907fbd785a694c3617d35a6ce49477ac5704d75f0e727e353da7bc664aacc2";
        var callType = getMethodName(calltype, "without");
        var node = acceptanceTestProperties.getNodeType();

        var data = encodeDataToByteArray(
                EQUIVALENCE_CALL,
                callType,
                TestUtil.asAddress("0x0000000000000000000000000000000000000002"),
                message.getBytes(StandardCharsets.UTF_8));
        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);

        var decodedResult = getDecodedInternalCallResult(response);
        assertEquals(hashedMessage, to32BytesString(String.valueOf(Bytes.wrap(decodedResult))));
    }

    @Then("I execute internal {string} against Ripemd-160 precompile")
    public void executeAllCallsForRipemd160(String calltype) {
        var message = "Encode me!";
        var hashedMessage = "4f0c39893f4c1c805aea87a95b5d359a218920d6";
        var callType = getMethodName(calltype, "without");
        var node = acceptanceTestProperties.getNodeType();

        var data = encodeDataToByteArray(
                EQUIVALENCE_CALL,
                callType,
                TestUtil.asAddress("0x0000000000000000000000000000000000000003"),
                message.getBytes(StandardCharsets.UTF_8));
        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, null);

        var decodedResult = getDecodedInternalCallResult(response);
        assertEquals(
                hashedMessage,
                String.valueOf(Bytes.wrap(decodedResult).trimLeadingZeros().toUnprefixedHexString()));
    }

    @Then("I execute internal {string} against PRNG precompile address {string} amount")
    public void executeInternalCallForPRNG(String call, String amountType) {
        var prngAddress = "0x0000000000000000000000000000000000000169";
        var callType = getMethodName(call, amountType);
        var node = acceptanceTestProperties.getNodeType();
        var data = encodeDataToByteArray(
                EQUIVALENCE_CALL,
                callType,
                TestUtil.asAddress(prngAddress),
                getFunctionSelectorBytes(PSEUDORANDOM_SEED_GENERATOR_SELECTOR).toArray());

        Hbar amount = null;
        if (amountType.equals("with")) {
            amount = Hbar.fromTinybars(10L);
        }

        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, amount);

        if (amountType.equals("with")) {
            // Should fail
            if (call.equals("callcode")) {
                assertArrayEquals(new byte[0], response.getMirrorNodeResponseAsBytes());
            } else {
                var decodedResult = getDecodedInternalCallResult(response);
                var isSuccess = getDecodedInternalCallSuccessFlag(response);
                assertFalse(isSuccess);
                assertArrayEquals(new byte[0], Bytes.wrap(decodedResult).toArray());
            }
        } else {
            // Should succeed
            if (call.equals("callcode")) {
                assertFalse(Arrays.equals(new byte[0], response.getMirrorNodeResponseAsBytes()));
            } else {
                var decodedResult = getDecodedInternalCallResult(response);
                var isSuccess = getDecodedInternalCallSuccessFlag(response);
                assertTrue(isSuccess);
                assertFalse(Arrays.equals(new byte[0], Bytes.wrap(decodedResult).toArray()));
            }
        }
    }

    @Then("I execute internal {string} against exchange rate precompile address {string} amount")
    public void executeInternalCallForExchangeRate(String call, String amountType) {
        var exchangeRateAddress = "0x0000000000000000000000000000000000000168";
        var callType = getMethodName(call, amountType);
        var node = acceptanceTestProperties.getNodeType();
        var functionParameterData = Bytes.concatenate(
                        getFunctionSelectorBytes(TO_TINYBARS_SELECTOR), getBigIntegerBytes(new BigInteger("100")))
                .toArray();
        var data = encodeDataToByteArray(
                EQUIVALENCE_CALL, callType, TestUtil.asAddress(exchangeRateAddress), functionParameterData);

        Hbar amount = null;
        if (amountType.equals("with")) {
            amount = Hbar.fromTinybars(10L);
        }

        final var response = callContract(node, StringUtils.EMPTY, EQUIVALENCE_CALL, callType, data, amount);

        if (amountType.equals("with")) {
            // Should fail
            var decodedResult = getDecodedInternalCallResult(response);
            var isSuccess = getDecodedInternalCallSuccessFlag(response);
            assertFalse(isSuccess);
            assertArrayEquals(new byte[0], Bytes.wrap(decodedResult).toArray());
        } else {
            // Should succeed
            var decodedResult = getDecodedInternalCallResult(response);
            var isSuccess = getDecodedInternalCallSuccessFlag(response);
            assertTrue(isSuccess);
            assertTrue(Bytes.wrap(decodedResult).toBigInteger().compareTo(BigInteger.ONE) > 0);
        }
    }

    private byte[] constructFunctionParameterDataForInternalCallToSystemAccount(
            long accountNumber, String call, String callType) {
        if (accountNumber == 359) { // HTS precompile
            return null;
        } else if (call.equals("callcode")) {
            return encodeDataToByteArray(EQUIVALENCE_CALL, GET_CURRENT_ADDRESS.getSelector());
        } else if (accountNumber >= 1 && accountNumber <= 9) {
            return encodeDataToByteArray(EQUIVALENCE_CALL, callType + "ToIdentityPrecompile");
        } else {
            return new byte[0];
        }
    }

    private void validateResponseFromCallToSystemAccount(
            long accountNumber,
            String call,
            Hbar amount,
            byte[] functionParameterData,
            GeneralContractExecutionResponse response) {
        var node = acceptanceTestProperties.getNodeType();
        if (node.equals(NodeNameEnum.CONSENSUS)) {
            if (amount == null) {
                assertThat(response.errorMessage()).isBlank();
            } else {
                if (shouldSystemAccountWithAmountReturnInvalidFeeSubmitted(accountNumber)) {
                    assertEquals(INVALID_FEE_SUBMITTED, response.errorMessage());
                } else {
                    assertThat(response.errorMessage()).isBlank();
                }
            }
        } else {
            if (call.equals("callcode")) {
                validateMirrorNodeCallCodeResponseToSystemAccount(
                        accountNumber, amount, functionParameterData, response);
            } else {
                validateMirrorNodeCallResponseToSystemAccount(accountNumber, amount, response);
            }
        }
    }

    private void validateMirrorNodeCallCodeResponseToSystemAccount(
            long accountNumber, Hbar amount, byte[] functionParameterData, GeneralContractExecutionResponse response) {
        var returnedDataBytes = response.contractCallResponse().getResultAsBytes();
        if (amount == null) {
            // Should succeed
            if (accountNumber > 0 && accountNumber <= 9) {
                assertArrayEquals(functionParameterData, response.contractCallResponse().getResultAsBytes().trimTrailingZeros().toArray());
            } else {
                assertArrayEquals(new byte[0], returnedDataBytes.toArray());
            }
        } else {
            if (shouldSystemAccountWithAmountReturnInvalidFeeSubmitted(accountNumber)) {
                // Should fail
                // TODO: add the assert when we know how the error will be returned
            } else {
                // Should succeed
                assertArrayEquals(new byte[0], returnedDataBytes.toArray());
            }
        }
    }

    private void validateMirrorNodeCallResponseToSystemAccount(
            long accountNumber, Hbar amount, GeneralContractExecutionResponse response) {
        TupleType tupleType;
        if (accountNumber == 359) {
            tupleType = TupleType.parse("(bool,bool)");
        } else {
            tupleType = TupleType.parse("(bool,bytes)");
        }
        var decodedResult = tupleType.decode(
                ByteString.fromHex(response.getMirrorNodeResponseAsString()).toByteArray());
        var isSuccess = (boolean) decodedResult.get(0);

        if (amount == null) {
            assertTrue(isSuccess);
        } else {
            if (shouldSystemAccountWithAmountReturnInvalidFeeSubmitted(accountNumber)) {
                assertFalse(isSuccess);
            } else {
                assertTrue(isSuccess);
            }
        }
    }

    public String getMethodName(String typeOfCall, String amountValue) {
        String combinedKey = typeOfCall + "_" + amountValue;

        return switch (combinedKey) {
            case "call_without" -> "makeCallWithoutAmount";
            case "call_with" -> "makeCallWithAmount";
            case "staticcall_without" -> "makeStaticCall";
            case "delegatecall_without" -> "makeDelegateCall";
            case "callcode_without" -> "callCodeToContractWithoutAmount";
            case "callcode_with" -> "callCodeToContractWithAmount";
            case "non_fungible_transferFrom" -> "transferFromNFTExternal";
            case "fungible_transferFrom" -> "transferFromExternal";
            case "fungible_transfer" -> "transferTokenExternal";
            case "non_fungible_transfer" -> "transferNFTExternal";
            default -> "Unknown";
        };
    }

    public String getMethodNameForHtsAccount(String typeOfCall, String amountValue) {
        String combinedKey = typeOfCall + "_" + amountValue;

        return switch (combinedKey) {
            case "call_without" -> "htsCallWithoutAmount";
            case "call_with" -> "htsCallWithAmount";
            case "staticcall_without" -> "htsStaticCall";
            case "delegatecall_without" -> "htsDelegateCall";
            case "callcode_without" -> "htsCallCodeToContractWithoutAmount";
            case "callcode_with" -> "htsCallCodeToContractWithAmount";
            default -> "Unknown";
        };
    }

    public static long extractAccountNumber(String account) {
        String[] parts = account.split("\\.");
        return Long.parseLong(parts[parts.length - 1]);
    }

    private boolean shouldSystemAccountWithAmountReturnInvalidFeeSubmitted(long accountNumber) {
        return accountNumber >= 0 && accountNumber <= 750;
    }

    private byte[] getDecodedInternalCallResult(GeneralContractExecutionResponse response) {
        return INTERNAL_CALL_RETURN_TYPE
                .decode(ByteString.fromHex(response.getMirrorNodeResponseAsString())
                        .toByteArray())
                .get(1);
    }

    private boolean getDecodedInternalCallSuccessFlag(GeneralContractExecutionResponse response) {
        return INTERNAL_CALL_RETURN_TYPE
                .decode(ByteString.fromHex(response.getMirrorNodeResponseAsString())
                        .toByteArray())
                .get(0);
    }

    private Bytes getFunctionSelectorBytes(int functionSelector) {
        return Bytes.ofUnsignedInt(functionSelector & 0xffffffffL);
    }

    private Bytes getBigIntegerBytes(BigInteger number) {
        return Bytes32.leftPad(Bytes.wrap(number.toByteArray()));
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        GET_BALANCE("getBalance"),
        DESTROY_CONTRACT("destroyContract"),
        COPY_CODE("copyCode"),
        GET_CODE_SIZE("getCodeSize"),
        GET_CODE_HASH("getCodeHash"),
        GET_CURRENT_ADDRESS("getCurrentAddress"),
        IS_TOKEN("isToken(address)");

        private final String selector;
    }
}
