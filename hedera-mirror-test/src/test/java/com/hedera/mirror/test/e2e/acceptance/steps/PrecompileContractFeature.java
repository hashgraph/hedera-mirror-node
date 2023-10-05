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

import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.PRECOMPILE_TEST_CONTRACT;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.ALLOWANCE_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.BALANCE_OF_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.DECIMALS_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_APPROVED_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_EXPIRY_INFO_FOR_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_INFORMATION_FOR_FUNGIBLE_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_INFORMATION_FOR_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_TOKEN_DEFAULT_FREEZE_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_TOKEN_DEFAULT_KYC_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_TOKEN_KEY_PUBLIC_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.GET_TYPE_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.IS_APPROVED_FOR_ALL_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.IS_KYC_GRANTED_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.IS_TOKEN_FROZEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.IS_TOKEN_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.NAME_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.OWNER_OF_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.SYMBOL_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.PrecompileContractFeature.ContractMethods.TOTAL_SUPPLY_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.ZERO_ADDRESS;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.FastHex;
import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.CustomRoyaltyFee;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorAccountResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorNftResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PrecompileContractFeature extends AbstractFeature {
    private static final long firstNftSerialNumber = 1;
    private final List<TokenId> tokenIds = new ArrayList<>();
    private final TokenClient tokenClient;
    private final MirrorNodeClient mirrorClient;
    private final AccountClient accountClient;
    private ExpandedAccountId ecdsaEaId;

    private DeployedContract deployedPrecompileContract;
    private String precompileTestContractSolidityAddress;

    @Given("I successfully create and verify a precompile contract from contract bytes")
    public void createNewContract() throws IOException {
        deployedPrecompileContract = getContract(PRECOMPILE_TEST_CONTRACT);
        precompileTestContractSolidityAddress =
                deployedPrecompileContract.contractId().toSolidityAddress();
    }

    @Given("I successfully create and verify a fungible token for precompile contract tests")
    public void createFungibleToken() {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(10);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());

        CustomFractionalFee customFractionalFee = new CustomFractionalFee();
        customFractionalFee.setFeeCollectorAccountId(admin.getAccountId());
        customFractionalFee.setNumerator(1);
        customFractionalFee.setDenominator(10);

        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                List.of(customFixedFee, customFractionalFee));
    }

    @Given("I successfully create and verify a non fungible token for precompile contract tests")
    public void createNonFungibleToken() {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        CustomFixedFee customFixedFee = new CustomFixedFee();
        customFixedFee.setAmount(10);
        customFixedFee.setFeeCollectorAccountId(admin.getAccountId());

        CustomRoyaltyFee customRoyaltyFee = new CustomRoyaltyFee();
        customRoyaltyFee.setNumerator(5);
        customRoyaltyFee.setDenominator(10);
        customRoyaltyFee.setFallbackFee(new CustomFixedFee().setHbarAmount(new Hbar(1)));
        customRoyaltyFee.setFeeCollectorAccountId(admin.getAccountId());

        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenSupplyType.INFINITE,
                List.of(customFixedFee, customRoyaltyFee));
    }

    @Given("I create an ecdsa account and associate it to the tokens")
    public void createEcdsaAccountAndAssociateItToTokens() {
        ecdsaEaId = accountClient.getAccount(AccountClient.AccountNameEnum.BOB);
        for (TokenId tokenId : tokenIds) {
            tokenClient.associate(ecdsaEaId, tokenId);
        }
    }

    @Given("I mint and verify a nft")
    public void mintNft() {
        NetworkTransactionResponse tx = tokenClient.mint(tokenIds.get(1), RandomUtils.nextBytes(4));
        assertNotNull(tx.getTransactionId());
        TransactionReceipt receipt = tx.getReceipt();
        assertNotNull(receipt);
        assertThat(receipt.serials.size()).isOne();

        verifyNft(tokenIds.get(1), firstNftSerialNumber);
    }

    @Then("the mirror node REST API should return status {int} for the latest transaction")
    public void verifyMirrorAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Then("check if fungible token is token")
    public void checkIfFungibleTokenIsToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, IS_TOKEN_SELECTOR, asAddress(tokenIds.get(0)));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertTrue(response.getResultAsBoolean());
    }

    @And("check if non fungible token is token")
    public void checkIfNonFungibleTokenIsToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, IS_TOKEN_SELECTOR, asAddress(tokenIds.get(1)));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertTrue(response.getResultAsBoolean());
    }

    @Then("the contract call REST API to is token with invalid account id should return an error")
    public void checkIfInvalidAccountIsToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, IS_TOKEN_SELECTOR, asAddress(ZERO_ADDRESS));
        String contractIdAsSolidityAddress = precompileTestContractSolidityAddress;
        String contractClientAddress = contractClient.getClientAddress();

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(data))
                .from(contractClientAddress)
                .to(contractIdAsSolidityAddress)
                .estimate(false)
                .build();

        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @And("the contract call REST API to is token with valid account id should return an error")
    public void checkIfValidAccountIsToken() {
        ByteBuffer encodedFunctionCall = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_TOKEN_SELECTOR,
                asAddress(accountClient.getTokenTreasuryAccount().getAccountId().toSolidityAddress()));
        String contractIdAsSolidityAddress = precompileTestContractSolidityAddress;
        String contractClientAddress = contractClient.getClientAddress();

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(Strings.encode(encodedFunctionCall))
                .from(contractClientAddress)
                .to(contractIdAsSolidityAddress)
                .estimate(false)
                .build();

        assertThatThrownBy(() -> mirrorClient.contractsCall(contractCallRequestBody))
                .isInstanceOf(WebClientResponseException.class)
                .hasMessageContaining("400 Bad Request from POST");
    }

    @And("verify fungible token isn't frozen")
    public void verifyFungibleTokenIsNotFrozen() {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_TOKEN_FROZEN_SELECTOR,
                asAddress(tokenIds.get(0)),
                asAddress(contractClient));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertFalse(response.getResultAsBoolean());
    }

    @And("verify non fungible token isn't frozen")
    public void verifyNonFungibleTokenIsNotFrozen() {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_TOKEN_FROZEN_SELECTOR,
                asAddress(tokenIds.get(1)),
                asAddress(contractClient));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertFalse(response.getResultAsBoolean());
    }

    @Given("I freeze a non fungible token")
    public void freezeToken() {
        NetworkTransactionResponse freezeResponse =
                tokenClient.freeze(tokenIds.get(1), contractClient.getClient().getOperatorAccountId());
        verifyTx(freezeResponse.getTransactionIdStringNoCheckSum());
    }

    @Retryable(
            value = {AssertionError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    @And("check if non fungible token is frozen")
    public void checkIfTokenIsFrozen() {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_TOKEN_FROZEN_SELECTOR,
                asAddress(tokenIds.get(1)),
                asAddress(contractClient));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertTrue(response.getResultAsBoolean());
    }

    @Given("I unfreeze a non fungible token")
    public void unfreezeToken() {
        NetworkTransactionResponse freezeResponse =
                tokenClient.unfreeze(tokenIds.get(1), contractClient.getClient().getOperatorAccountId());
        verifyTx(freezeResponse.getTransactionIdStringNoCheckSum());
    }

    @Retryable(
            value = {AssertionError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    @And("check if non fungible token is unfrozen")
    public void checkIfTokenIsUnfrozen() {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_TOKEN_FROZEN_SELECTOR,
                asAddress(tokenIds.get(1)),
                asAddress(contractClient));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertFalse(response.getResultAsBoolean());
    }

    @Given("I freeze fungible token for evm address")
    public void freezeTokenForEvmAddress() {
        NetworkTransactionResponse freezeResponse = tokenClient.freeze(tokenIds.get(0), ecdsaEaId.getAccountId());
        verifyTx(freezeResponse.getTransactionIdStringNoCheckSum());
    }

    @Retryable(
            value = {AssertionError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    @And("check if fungible token is frozen for evm address")
    public void checkIfTokenIsFrozenForEvmAddress() {
        MirrorAccountResponse accountInfo = mirrorClient.getAccountDetailsByAccountId(ecdsaEaId.getAccountId());
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_TOKEN_FROZEN_SELECTOR,
                asAddress(tokenIds.get(0)),
                asAddress(accountInfo.getEvmAddress()));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertTrue(response.getResultAsBoolean());
    }

    @Given("I unfreeze fungible token for evm address")
    public void unfreezeTokenForEvmAddress() {
        NetworkTransactionResponse unfreezeResponse = tokenClient.unfreeze(tokenIds.get(0), ecdsaEaId.getAccountId());
        verifyTx(unfreezeResponse.getTransactionIdStringNoCheckSum());
    }

    @Retryable(
            value = {AssertionError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    @And("check if fungible token is unfrozen for evm address")
    public void checkIfTokenIsUnfrozenForEvmAddress() {
        MirrorAccountResponse accountInfo = mirrorClient.getAccountDetailsByAccountId(ecdsaEaId.getAccountId());
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_TOKEN_FROZEN_SELECTOR,
                asAddress(tokenIds.get(0)),
                asAddress(accountInfo.getEvmAddress()));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertFalse(response.getResultAsBoolean());
    }

    @Retryable(
            value = {AssertionError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyTx(String txId) {
        MirrorTransactionsResponse txResponse = mirrorClient.getTransactions(txId);
        assertNotNull(txResponse);
    }

    @And("check if fungible token is kyc granted")
    public void checkIfFungibleTokenIsKycGranted() {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_KYC_GRANTED_SELECTOR,
                asAddress(tokenIds.get(0)),
                asAddress(contractClient));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertTrue(response.getResultAsBoolean());
    }

    @And("check if non fungible token is kyc granted")
    public void checkIfNonFungibleTokenIsKycGranted() {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                IS_KYC_GRANTED_SELECTOR,
                asAddress(tokenIds.get(1)),
                asAddress(contractClient));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertTrue(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the default freeze for a fungible token")
    public void getDefaultFreezeOfFungibleToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_TOKEN_DEFAULT_FREEZE_SELECTOR, asAddress(tokenIds.get(0)));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the default freeze for a non fungible token")
    public void getDefaultFreezeOfNonFungibleToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_TOKEN_DEFAULT_FREEZE_SELECTOR, asAddress(tokenIds.get(1)));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the default kyc for a fungible token")
    public void getDefaultKycOfFungibleToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_TOKEN_DEFAULT_KYC_SELECTOR, asAddress(tokenIds.get(0)));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the default kyc for a non fungible token")
    public void getDefaultKycOfNonFungibleToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_TOKEN_DEFAULT_KYC_SELECTOR, asAddress(tokenIds.get(1)));

        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the information for token for a fungible token")
    public void getInformationForTokenOfFungibleToken() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_INFORMATION_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(0)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple tokenInfo = baseGetInformationForTokenChecks(response);
        Long totalSupply = tokenInfo.get(1);
        assertThat(totalSupply).isEqualTo(1000000);
    }

    @Retryable(
            value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    @And("the contract call REST API should return the information for token for a non fungible token")
    public void getInformationForTokenOfNonFungibleToken() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_INFORMATION_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(1)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple tokenInfo = baseGetInformationForTokenChecks(response);
        Long totalSupply = tokenInfo.get(1);
        assertThat(totalSupply).isEqualTo(1);
    }

    @And("the contract call REST API should return the information for a fungible token")
    public void getInformationForFungibleToken() throws Exception {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT, GET_INFORMATION_FOR_FUNGIBLE_TOKEN_SELECTOR, asAddress(tokenIds.get(0)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple result = decodeFunctionResult("getInformationForFungibleToken", response);
        assertThat(result).isNotEmpty();

        Tuple tokenInfo = result.get(0);
        Tuple token = tokenInfo.get(0);
        int decimals = tokenInfo.get(1);

        assertFalse(token.isEmpty());
        assertThat(decimals).isEqualTo(10);
    }

    @And("the contract call REST API should return the information for a non fungible token")
    public void getInformationForNonFungibleToken() throws Exception {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN_SELECTOR,
                asAddress(tokenIds.get(1)),
                firstNftSerialNumber);
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple result = decodeFunctionResult("getInformationForNonFungibleToken", response);
        assertThat(result).isNotEmpty();

        Tuple tokenInfo = result.get(0);
        Tuple token = tokenInfo.get(0);
        long serialNumber = tokenInfo.get(1);
        String ownerId = tokenInfo.get(2).toString();
        long creationTime = tokenInfo.get(3);
        byte[] metadata = tokenInfo.get(4);
        String spenderId = tokenInfo.get(5).toString();

        assertThat(token).isNotEmpty();
        assertThat(serialNumber).isEqualTo(firstNftSerialNumber);
        assertThat(ownerId).isNotBlank();
        assertThat(creationTime).isPositive();
        assertThat(metadata).isNotEmpty();
        assertThat(spenderId).isEqualTo(ZERO_ADDRESS);
    }

    @And("the contract call REST API should return the type for a fungible token")
    public void getTypeForFungibleToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_TYPE_SELECTOR, asAddress(tokenIds.get(0)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertThat(response.getResultAsNumber()).isZero();
    }

    @And("the contract call REST API should return the type for a non fungible token")
    public void getTypeForNonFungibleToken() {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_TYPE_SELECTOR, asAddress(tokenIds.get(1)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        assertThat(response.getResultAsNumber()).isEqualTo(1);
    }

    @And("the contract call REST API should return the expiry token info for a fungible token")
    public void getExpiryTokenInfoForFungibleToken() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_EXPIRY_INFO_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(0)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        baseExpiryInfoChecks(response);
    }

    @And("the contract call REST API should return the expiry token info for a non fungible token")
    public void getExpiryTokenInfoForNonFungibleToken() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_EXPIRY_INFO_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(1)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        baseExpiryInfoChecks(response);
    }

    @And("the contract call REST API should return the token key for a fungible token")
    public void getTokenKeyForFungibleToken() throws Exception {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                GET_TOKEN_KEY_PUBLIC_SELECTOR,
                asAddress(tokenIds.get(0)),
                new BigInteger("1"));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple result = decodeFunctionResult("getTokenKeyPublic", response);
        assertThat(result).isNotEmpty();

        tokenKeyCheck(result);
    }

    @And("the contract call REST API should return the token key for a non fungible token")
    public void getTokenKeyForNonFungibleToken() throws Exception {
        var data = encodeData(
                PRECOMPILE_TEST_CONTRACT,
                GET_TOKEN_KEY_PUBLIC_SELECTOR,
                asAddress(tokenIds.get(1)),
                new BigInteger("1"));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple result = decodeFunctionResult("getTokenKeyPublic", response);
        assertThat(result).isNotEmpty();

        tokenKeyCheck(result);
    }

    @Retryable(
            value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyToken(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());
    }

    @Retryable(
            value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyNft(TokenId tokenId, Long serialNumber) {
        MirrorNftResponse mirrorNft = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);

        assertNotNull(mirrorNft);
        assertThat(mirrorNft.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(mirrorNft.getSerialNumber()).isEqualTo(serialNumber);
    }

    @And("the contract call REST API should return the name by direct call for a fungible token")
    public void getFungibleTokenNameByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(NAME_SELECTOR.getSelector()).encodeCallWithArgs(),
                tokenIds.get(0).toSolidityAddress());
        assertThat(response.getResultAsAsciiString()).contains("_name");
    }

    @And("the contract call REST API should return the symbol by direct call for a fungible token")
    public void getFungibleTokenSymbolByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(SYMBOL_SELECTOR.getSelector()).encodeCallWithArgs(),
                tokenIds.get(0).toSolidityAddress());
        assertThat(response.getResultAsAsciiString()).isNotEmpty();
    }

    @And("the contract call REST API should return the decimals by direct call for a  fungible token")
    public void getFungibleTokenDecimalsByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(DECIMALS_SELECTOR.getSelector()).encodeCallWithArgs(),
                tokenIds.get(0).toSolidityAddress());
        assertThat(response.getResultAsNumber()).isEqualTo(10);
    }

    @And("the contract call REST API should return the total supply by direct call for a  fungible token")
    public void getFungibleTokenTotalSupplyByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(TOTAL_SUPPLY_SELECTOR.getSelector()).encodeCallWithArgs(),
                tokenIds.get(0).toSolidityAddress());
        assertThat(response.getResultAsNumber()).isEqualTo(1000000);
    }

    @And("the contract call REST API should return the balanceOf by direct call for a fungible token")
    public void getFungibleTokenBalanceOfByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(BALANCE_OF_SELECTOR.getSelector()).encodeCallWithArgs(asAddress(contractClient)),
                tokenIds.get(0).toSolidityAddress());
        assertThat(response.getResultAsNumber()).isEqualTo(1000000);
    }

    @And("the contract call REST API should return the allowance by direct call for a fungible token")
    public void getFungibleTokenAllowanceByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(ALLOWANCE_SELECTOR.getSelector())
                        .encodeCallWithArgs(asAddress(contractClient), asAddress(ecdsaEaId)),
                tokenIds.get(0).toSolidityAddress());
        assertThat(response.getResultAsNumber()).isZero();
    }

    @And("the contract call REST API should return the name by direct call for a non fungible token")
    public void getNonFungibleTokenNameByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(NAME_SELECTOR.getSelector()).encodeCallWithArgs(),
                tokenIds.get(1).toSolidityAddress());
        assertThat(response.getResultAsAsciiString()).contains("_name");
    }

    @And("the contract call REST API should return the symbol by direct call for a non fungible token")
    public void getNonFungibleTokenSymbolByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(SYMBOL_SELECTOR.getSelector()).encodeCallWithArgs(),
                tokenIds.get(1).toSolidityAddress());
        assertThat(response.getResultAsAsciiString()).isNotEmpty();
    }

    @And("the contract call REST API should return the total supply by direct call for a non fungible token")
    public void getNonFungibleTokenTotalSupplyByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(TOTAL_SUPPLY_SELECTOR.getSelector()).encodeCallWithArgs(),
                tokenIds.get(1).toSolidityAddress());
        assertThat(response.getResultAsNumber()).isEqualTo(1);
    }

    @And("the contract call REST API should return the ownerOf by direct call for a non fungible token")
    public void getNonFungibleTokenOwnerOfByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(OWNER_OF_SELECTOR.getSelector()).encodeCallWithArgs(new BigInteger("1")),
                tokenIds.get(1).toSolidityAddress());
        tokenClient.validateAddress(response.getResultAsAddress());
    }

    @And("the contract call REST API should return the getApproved by direct call for a non fungible token")
    public void getNonFungibleTokenGetApprovedByDirectCall() {
        ContractCallResponse response = callContract(
                new Function(GET_APPROVED_SELECTOR.getSelector()).encodeCallWithArgs(new BigInteger("1")),
                tokenIds.get(0).toSolidityAddress());
        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the isApprovedForAll by direct call for a non fungible token")
    public void getNonFungibleTokenIsApprovedForAllByDirectCallOwner() {
        ContractCallResponse response = callContract(
                new Function(IS_APPROVED_FOR_ALL_SELECTOR.getSelector())
                        .encodeCallWithArgs(asAddress(contractClient), asAddress(ecdsaEaId)),
                tokenIds.get(0).toSolidityAddress());
        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the custom fees for a fungible token")
    public void getCustomFeesForFungibleToken() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(0)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);
        Tuple result = decodeFunctionResult("getCustomFeesForToken", response);
        assertThat(result).isNotEmpty();
        baseFixedFeeCheck(result.get(0));
        Tuple[] fractionalFees = result.get(1);
        Tuple fractionalFee = fractionalFees[0];
        Tuple[] royaltyFees = result.get(2);
        assertThat(fractionalFees).isNotEmpty();
        assertThat((long) fractionalFee.get(0)).isOne();
        assertThat((long) fractionalFee.get(1)).isEqualTo(10);
        assertThat((long) fractionalFee.get(2)).isZero();
        assertThat((long) fractionalFee.get(3)).isZero();
        assertThat((boolean) fractionalFee.get(4)).isFalse();
        assertThat(royaltyFees).isEmpty();
    }

    @And("the contract call REST API should return the custom fees for a non fungible token")
    public void getCustomFeesForNonFungibleToken() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(1)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);
        Tuple result = decodeFunctionResult("getCustomFeesForToken", response);
        assertThat(result).isNotEmpty();
        baseFixedFeeCheck(result.get(0));
        Tuple[] fractionalFees = result.get(1);
        Tuple[] royaltyFees = result.get(2);
        assertThat(fractionalFees).isEmpty();
        assertThat(royaltyFees).isNotEmpty();
    }

    // ETHCALL-032
    @And(
            "I call function with HederaTokenService getTokenCustomFees token - fractional fee and fixed fee - fungible token")
    public void getCustomFeesForFungibleTokenFractionalAndFixedFees() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(0)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple result = decodeFunctionResult("getCustomFeesForToken", response);
        assertThat(result).isNotEmpty();
        baseFixedFeeCheck(result.get(0));
        Tuple[] fractionalFees = result.get(1);
        Tuple fractionalFee = fractionalFees[0];
        assertThat(fractionalFees).isNotEmpty();
        assertThat((long) fractionalFee.get(0)).isOne();
        assertThat((long) fractionalFee.get(1)).isEqualTo(10);
        assertThat((long) fractionalFee.get(2)).isZero();
        assertThat((long) fractionalFee.get(3)).isZero();
        assertFalse((boolean) fractionalFee.get(4));
        assertThat(fractionalFee.get(5).toString().toLowerCase())
                .isEqualTo("0x" + contractClient.getClientAddress().toLowerCase());
    }

    // ETHCALL-033
    @And("I call function with HederaTokenService getTokenCustomFees token - royalty fee")
    public void getCustomFeesForFungibleTokenRoyaltyFee() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(1)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple result = decodeFunctionResult("getCustomFeesForToken", response);
        assertThat(result).isNotEmpty();
        Tuple[] royaltyFees = result.get(2);
        Tuple royaltyFee = royaltyFees[0];
        assertThat((long) royaltyFee.get(0)).isEqualTo(5);
        assertThat((long) royaltyFee.get(1)).isEqualTo(10);
        assertThat(royaltyFee.get(5).toString().toLowerCase())
                .isEqualTo("0x"
                        + tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress());
    }

    // ETHCALL-034
    @And("I call function with HederaTokenService getTokenCustomFees token - royalty fee + fallback")
    public void getCustomFeesForFungibleTokenRoyaltyFeeAndFallback() throws Exception {
        var data = encodeData(PRECOMPILE_TEST_CONTRACT, GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR, asAddress(tokenIds.get(1)));
        ContractCallResponse response = callContract(data, precompileTestContractSolidityAddress);

        Tuple result = decodeFunctionResult("getCustomFeesForToken", response);
        assertThat(result).isNotEmpty();
        Tuple[] royaltyFees = result.get(2);
        Tuple royaltyFee = royaltyFees[0];
        assertThat((long) royaltyFee.get(2)).isEqualTo(new Hbar(1).toTinybars());
        assertThat(royaltyFee.get(3).toString()).hasToString(ZERO_ADDRESS);
        assertTrue((boolean) royaltyFee.get(4));
        assertThat(royaltyFee.get(5).toString().toLowerCase())
                .hasToString("0x"
                        + tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress());
    }

    private void tokenKeyCheck(final Tuple result) {
        Tuple keyValue = result.get(0);
        boolean inheritAccountKey = keyValue.get(0);
        String contractId = keyValue.get(1).toString();
        byte[] ed25519 = ((Tuple) result.get(0)).get(2);
        byte[] ecdsa = ((Tuple) result.get(0)).get(3);
        String delegatableContractId = keyValue.get(4).toString();

        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        if (admin.getPublicKey().isED25519()) {
            assertThat(ed25519).isNotEmpty();
            assertThat(ecdsa).isEmpty();
        } else if (admin.getPublicKey().isECDSA()) {
            assertThat(ed25519).isEmpty();
            assertThat(ecdsa).isNotEmpty();
        }

        assertThat(keyValue).isNotEmpty();
        assertFalse(inheritAccountKey);
        assertThat(contractId).isEqualTo(ZERO_ADDRESS);
        assertThat(delegatableContractId).isEqualTo(ZERO_ADDRESS);
    }

    private void baseFixedFeeCheck(Tuple[] fixedFees) {
        assertThat(fixedFees).isNotEmpty();
        Tuple fixedFee = fixedFees[0];
        assertThat((long) fixedFee.get(0)).isEqualTo(10);
        assertThat(fixedFee.get(1).toString()).hasToString(ZERO_ADDRESS);
        assertTrue((boolean) fixedFee.get(2));
        assertFalse((boolean) fixedFee.get(3));
        contractClient.validateAddress(fixedFee.get(4).toString().toLowerCase().replace("0x", ""));
    }

    private Tuple baseGetInformationForTokenChecks(ContractCallResponse response) throws Exception {
        Tuple result = decodeFunctionResult("getInformationForToken", response);
        assertThat(result).isNotEmpty();

        Tuple tokenInfo = result.get(0);
        Tuple token = tokenInfo.get(0);
        boolean deleted = tokenInfo.get(2);
        boolean defaultKycStatus = tokenInfo.get(3);
        boolean pauseStatus = tokenInfo.get(4);
        Tuple[] fixedFees = tokenInfo.get(5);
        String ledgerId = tokenInfo.get(8);

        assertFalse(token.isEmpty());
        assertFalse(deleted);
        assertFalse(defaultKycStatus);
        assertFalse(pauseStatus);
        baseFixedFeeCheck(fixedFees);
        assertThat(ledgerId).isNotBlank();

        return tokenInfo;
    }

    private void baseExpiryInfoChecks(ContractCallResponse response) throws Exception {
        Tuple result = decodeFunctionResult("getExpiryInfoForToken", response);
        assertThat(result).isNotEmpty();

        Tuple expiryInfo = result.get(0);
        assertThat(expiryInfo).isNotEmpty();
        assertThat(expiryInfo.size()).isEqualTo(3);
    }

    private void createNewToken(
            String symbol, TokenType tokenType, TokenSupplyType tokenSupplyType, List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                TokenFreezeStatus.Unfrozen_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                admin,
                1_000_000,
                tokenSupplyType,
                1_000_000,
                tokenType,
                customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
        tokenIds.add(tokenId);

        verifyToken(tokenId);
    }

    private Tuple decodeFunctionResult(String functionName, ContractCallResponse response) throws Exception {
        Optional<Object> function;
        try (var in = getResourceAsStream(PRECOMPILE_TEST_CONTRACT.getPath())) {
            function = Arrays.stream(readCompiledArtifact(in).getAbi())
                    .filter(item -> ((LinkedHashMap) item).get("name").equals(functionName))
                    .findFirst();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            String abiFunctionAsJsonString = (new JSONObject((Map) function.get())).toString();
            return Function.fromJson(abiFunctionAsJsonString)
                    .decodeReturn(FastHex.decode(response.getResult().replace("0x", "")));
        } catch (Exception e) {
            throw new Exception("Function not found in abi.");
        }
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
        IS_TOKEN_SELECTOR("isTokenAddress"),
        IS_TOKEN_FROZEN_SELECTOR("isTokenFrozen"),
        IS_KYC_GRANTED_SELECTOR("isKycGranted"),
        GET_TOKEN_DEFAULT_FREEZE_SELECTOR("getTokenDefaultFreeze"),
        GET_TOKEN_DEFAULT_KYC_SELECTOR("getTokenDefaultKyc"),
        GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR("getCustomFeesForToken"),
        GET_INFORMATION_FOR_TOKEN_SELECTOR("getInformationForToken"),
        GET_INFORMATION_FOR_FUNGIBLE_TOKEN_SELECTOR("getInformationForFungibleToken"),
        GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN_SELECTOR("getInformationForNonFungibleToken"),
        GET_TYPE_SELECTOR("getType"),
        GET_EXPIRY_INFO_FOR_TOKEN_SELECTOR("getExpiryInfoForToken"),
        GET_TOKEN_KEY_PUBLIC_SELECTOR("getTokenKeyPublic"),
        NAME_SELECTOR("name()"),
        SYMBOL_SELECTOR("symbol()"),
        DECIMALS_SELECTOR("decimals()"),
        TOTAL_SUPPLY_SELECTOR("totalSupply()"),
        BALANCE_OF_SELECTOR("balanceOf(address)"),
        ALLOWANCE_SELECTOR("allowance(address,address)"),
        OWNER_OF_SELECTOR("ownerOf(uint256)"),
        GET_APPROVED_SELECTOR("getApproved(uint256)"),
        IS_APPROVED_FOR_ALL_SELECTOR("isApprovedForAll(address,address)");
        private final String selector;
    }
}
