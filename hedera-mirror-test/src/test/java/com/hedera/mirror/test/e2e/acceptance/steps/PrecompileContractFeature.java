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

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.ZERO_ADDRESS;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.util.FastHex;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.*;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.*;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.*;
import com.hedera.mirror.test.e2e.acceptance.util.TestUtil;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PrecompileContractFeature extends AbstractFeature {
    public static final String IS_TOKEN_SELECTOR = "bff9834f";
    public static final String IS_TOKEN_FROZEN_SELECTOR = "565ca6fa";
    public static final String IS_KYC_GRANTED_SELECTOR = "bc2fb00e";
    public static final String GET_TOKEN_DEFAULT_FREEZE_SELECTOR = "319a8723";
    public static final String GET_TOKEN_DEFAULT_KYC_SELECTOR = "fd4d1c26";
    public static final String GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR = "44f38bc8";
    public static final String GET_INFORMATION_FOR_TOKEN_SELECTOR = "35589a13";
    public static final String GET_INFORMATION_FOR_FUNGIBLE_TOKEN_SELECTOR = "59c16f5a";
    public static final String GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN_SELECTOR = "8e5e7996";
    public static final String GET_TYPE_SELECTOR = "f429f19b";
    public static final String GET_EXPIRY_INFO_FOR_TOKEN_SELECTOR = "1de8edad";
    public static final String GET_TOKEN_KEY_PUBLIC_SELECTOR = "1955de0b";
    public static final String NAME_SELECTOR = "06fdde03";
    public static final String SYMBOL_SELECTOR = "95d89b41";
    public static final String DECIMALS_SELECTOR = "313ce567";
    public static final String TOTAL_SUPPLY_SELECTOR = "18160ddd";
    public static final String BALANCE_OF_SELECTOR = "70a08231";
    public static final String ALLOWANCE_SELECTOR = "dd62ed3e";
    public static final String OWNER_OF_SELECTOR = "6352211e";
    public static final String GET_APPROVED_SELECTOR = "081812fc";
    public static final String IS_APPROVED_FOR_ALL_SELECTOR = "e985e9c5";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final long firstNftSerialNumber = 1;
    private final List<TokenId> tokenIds = new ArrayList<>();
    private final ContractClient contractClient;
    private final TokenClient tokenClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;
    private final AccountClient accountClient;
    private ExpandedAccountId ecdsaEaId;

    @Value("classpath:solidity/artifacts/contracts/PrecompileTestContract.sol/PrecompileTestContract.json")
    private Resource precompileTestContract;

    private ContractId contractId;
    private FileId fileId;
    private CompiledSolidityArtifact compiledSolidityArtifact;

    @Before
    public void initialization() throws Exception {
        try (var in = precompileTestContract.getInputStream()) {
            compiledSolidityArtifact = MAPPER.readValue(in, CompiledSolidityArtifact.class);
        }
    }

    @After
    public void cleanup() {
        for (TokenId tokenId : tokenIds) {
            ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
            try {
                tokenClient.delete(admin, tokenId);
            } catch (Exception e) {
                log.warn("Error cleaning up token {} and associations error: {}", tokenId, e);
            }
        }
        tokenIds.clear();
    }

    @Given("I successfully create and verify a precompile contract from contract bytes")
    public void createNewContract() throws IOException {
        createContract(compiledSolidityArtifact.getBytecode());
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

        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenSupplyType.INFINITE,
                List.of(customFixedFee));
    }

    @Given("I create an ecdsa account and associate it to the tokens")
    public void createEcdsaAccountAndAssociateItToTokens() {
        ecdsaEaId = accountClient.createNewECDSAAccount(1_000_000_000);
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
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_TOKEN_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertTrue(response.getResultAsBoolean());
    }

    @And("check if non fungible token is token")
    public void checkIfNonFungibleTokenIsToken() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_TOKEN_SELECTOR + to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertTrue(response.getResultAsBoolean());
    }

    @Then("the contract call REST API to is token with invalid account id should return an error")
    public void checkIfInvalidAccountIsToken() {
        String selectorWithData = PrecompileContractFeature.IS_TOKEN_SELECTOR + TestUtil.to32BytesString(ZERO_ADDRESS);
        String contractIdAsSolidityAddress = contractId.toSolidityAddress();
        String contractClientAddress = contractClient.getClientAddress();

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(selectorWithData)
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
        String selectorWithData = PrecompileContractFeature.IS_TOKEN_SELECTOR
                + TestUtil.to32BytesString(
                        accountClient.getTokenTreasuryAccount().getAccountId().toSolidityAddress());
        String contractIdAsSolidityAddress = contractId.toSolidityAddress();
        String contractClientAddress = contractClient.getClientAddress();

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(selectorWithData)
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
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_TOKEN_FROZEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertFalse(response.getResultAsBoolean());
    }

    @And("verify non fungible token isn't frozen")
    public void verifyNonFungibleTokenIsNotFrozen() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_TOKEN_FROZEN_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

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
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_TOKEN_FROZEN_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

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
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_TOKEN_FROZEN_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
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

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_TOKEN_FROZEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(accountInfo.getEvmAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
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

        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_TOKEN_FROZEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(accountInfo.getEvmAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
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
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_KYC_GRANTED_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertFalse(response.getResultAsBoolean());
    }

    @And("check if non fungible token is kyc granted")
    public void checkIfNonFungibleTokenIsKycGranted() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_KYC_GRANTED_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the default freeze for a fungible token")
    public void getDefaultFreezeOfFungibleToken() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_TOKEN_DEFAULT_FREEZE_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the default freeze for a non fungible token")
    public void getDefaultFreezeOfNonFungibleToken() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_TOKEN_DEFAULT_FREEZE_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the default kyc for a fungible token")
    public void getDefaultKycOfFungibleToken() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_TOKEN_DEFAULT_KYC_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the default kyc for a non fungible token")
    public void getDefaultKycOfNonFungibleToken() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_TOKEN_DEFAULT_KYC_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the information for token for a fungible token")
    public void getInformationForTokenOfFungibleToken() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_INFORMATION_FOR_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

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
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_INFORMATION_FOR_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        Tuple tokenInfo = baseGetInformationForTokenChecks(response);
        Long totalSupply = tokenInfo.get(1);
        assertThat(totalSupply).isEqualTo(1);
    }

    @And("the contract call REST API should return the information for a fungible token")
    public void getInformationForFungibleToken() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_INFORMATION_FOR_FUNGIBLE_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

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
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(String.valueOf(firstNftSerialNumber)))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

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
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_TYPE_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsNumber()).isZero();
    }

    @And("the contract call REST API should return the type for a non fungible token")
    public void getTypeForNonFungibleToken() {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_TYPE_SELECTOR + to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        assertThat(response.getResultAsNumber()).isEqualTo(1);
    }

    @And("the contract call REST API should return the expiry token info for a fungible token")
    public void getExpiryTokenInfoForFungibleToken() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_EXPIRY_INFO_FOR_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        baseExpiryInfoChecks(response);
    }

    @And("the contract call REST API should return the expiry token info for a non fungible token")
    public void getExpiryTokenInfoForNonFungibleToken() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_EXPIRY_INFO_FOR_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        baseExpiryInfoChecks(response);
    }

    @And("the contract call REST API should return the token key for a fungible token")
    public void getTokenKeyForFungibleToken() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_TOKEN_KEY_PUBLIC_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(String.valueOf(firstNftSerialNumber)))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

        Tuple result = decodeFunctionResult("getTokenKeyPublic", response);
        assertThat(result).isNotEmpty();

        tokenKeyCheck(result);
    }

    @And("the contract call REST API should return the token key for a non fungible token")
    public void getTokenKeyForNonFungibleToken() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_TOKEN_KEY_PUBLIC_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(String.valueOf(firstNftSerialNumber)))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);

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
    public void getFungibleTokenNameByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(NAME_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsAsciiString()).contains("_name");
    }

    @And("the contract call REST API should return the symbol by direct call for a fungible token")
    public void getFungibleTokenSymbolByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(SYMBOL_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsAsciiString()).isNotEmpty();
    }

    @And("the contract call REST API should return the decimals by direct call for a  fungible token")
    public void getFungibleTokenDecimalsByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(DECIMALS_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsNumber()).isEqualTo(10);
    }

    @And("the contract call REST API should return the total supply by direct call for a  fungible token")
    public void getFungibleTokenTotalSupplyByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(TOTAL_SUPPLY_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsNumber()).isEqualTo(1000000);
    }

    @And("the contract call REST API should return the balanceOf by direct call for a fungible token")
    public void getFungibleTokenBalanceOfByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(BALANCE_OF_SELECTOR + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsNumber()).isEqualTo(1000000);
    }

    @And("the contract call REST API should return the allowance by direct call for a fungible token")
    public void getFungibleTokenAllowanceByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(ALLOWANCE_SELECTOR
                        + to32BytesString(contractClient.getClientAddress())
                        + to32BytesString(ecdsaEaId.getAccountId().toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsNumber()).isZero();
    }

    @And("the contract call REST API should return the name by direct call for a non fungible token")
    public void getNonFungibleTokenNameByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(NAME_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsAsciiString()).contains("_name");
    }

    @And("the contract call REST API should return the symbol by direct call for a non fungible token")
    public void getNonFungibleTokenSymbolByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(SYMBOL_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsAsciiString()).isNotEmpty();
    }

    @And("the contract call REST API should return the total supply by direct call for a non fungible token")
    public void getNonFungibleTokenTotalSupplyByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(TOTAL_SUPPLY_SELECTOR)
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(1).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertThat(response.getResultAsNumber()).isEqualTo(1);
    }

    @And("the contract call REST API should return the ownerOf by direct call for a non fungible token")
    public void getNonFungibleTokenOwnerOfByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(OWNER_OF_SELECTOR + to32BytesString(String.valueOf(firstNftSerialNumber)))
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(1).toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        tokenClient.validateAddress(response.getResultAsAddress());
    }

    @And("the contract call REST API should return the getApproved by direct call for a non fungible token")
    public void getNonFungibleTokenGetApprovedByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_APPROVED_SELECTOR + to32BytesString(String.valueOf(firstNftSerialNumber)))
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the isApprovedForAll by direct call for a non fungible token")
    public void getNonFungibleTokenIsApprovedForAllByDirectCall() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(IS_APPROVED_FOR_ALL_SELECTOR
                        + to32BytesString(contractClient.getClientAddress())
                        + to32BytesString(ecdsaEaId.getAccountId().toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(tokenIds.get(0).toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        assertFalse(response.getResultAsBoolean());
    }

    @And("the contract call REST API should return the custom fees for a fungible token")
    public void getCustomFeesForFungibleToken() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
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
        assertFalse((boolean) fractionalFee.get(4));
        contractClient.validateAddress(
                fractionalFee.get(5).toString().toLowerCase().replace("0x", ""));
        assertThat(royaltyFees).isEmpty();
    }

    @And("the contract call REST API should return the custom fees for a non fungible token")
    public void getCustomFeesForNonFungibleToken() throws Exception {
        var contractCallRequestBody = ContractCallRequest.builder()
                .data(GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        ContractCallResponse response = mirrorClient.contractsCall(contractCallRequestBody);
        Tuple result = decodeFunctionResult("getCustomFeesForToken", response);
        assertThat(result).isNotEmpty();
        baseFixedFeeCheck(result.get(0));
        Tuple[] fractionalFees = result.get(1);
        Tuple[] royaltyFees = result.get(2);
        assertThat(fractionalFees).isEmpty();
        assertThat(royaltyFees).isEmpty();
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

    private void createContract(String byteCode) {
        persistContractBytes(byteCode.replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                null,
                null);

        verifyCreateContractNetworkResponse();
    }

    private void persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[] {});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);

        networkTransactionResponse = fileClient.appendFile(fileId, contractContents.getBytes(StandardCharsets.UTF_8));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void verifyCreateContractNetworkResponse() {
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        contractId = networkTransactionResponse.getReceipt().contractId;
        assertNotNull(contractId);
    }

    private Tuple decodeFunctionResult(String functionName, ContractCallResponse response) throws Exception {
        Optional<Object> function = Arrays.stream(compiledSolidityArtifact.getAbi())
                .filter(item -> ((LinkedHashMap) item).get("name").equals(functionName))
                .findFirst();

        try {
            String abiFunctionAsJsonString = (new JSONObject((Map) function.get())).toString();
            return Function.fromJson(abiFunctionAsJsonString)
                    .decodeReturn(FastHex.decode(response.getResult().replace("0x", "")));
        } catch (Exception e) {
            throw new Exception("Function not found in abi.");
        }
    }
}
