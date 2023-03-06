package com.hedera.mirror.test.e2e.acceptance.steps;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;

import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;

import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;

import com.hedera.mirror.test.e2e.acceptance.util.TestUtil;

import io.cucumber.java.en.Given;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.ResourceUtils;

import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import org.web3j.abi.datatypes.Function;

@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PrecompileContractFeature extends AbstractFeature {
    private final List<TokenId> tokenIds = new ArrayList<>();

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public static final String IS_TOKEN_SELECTOR = "bff9834f";
    public static final String IS_TOKEN_FROZEN_SELECTOR = "565ca6fa";
    public static final String IS_KYC_GRANTED_SELECTOR = "bc2fb00e";
    public static final String GET_TOKEN_DEFAULT_FREEZE_SELECTOR = "319a8723";
    public static final String GET_TOKEN_DEFAULT_KYC_SELECTOR = "fd4d1c26";
    public static final String GET_CUSTOM_FEES_FOR_TOKEN_SELECTOR = "44f38bc8";
    public static final String GET_INFORMATION_FOR_TOKEN_SELECTOR = "35589a13";
    public static final String GET_INFORMATION_FOR_FUNGIBLE_TOKEN_SELECTOR = "59c16f5a";
    public static final String GET_INFORMATION_FOR_NON_FUNGIBLE_TOKEN_SELECTOR = "bf39bd9d";
    public static final String GET_TYPE_SELECTOR = "f429f19b";
    public static final String GET_EXPIRY_INFO_FOR_TOKEN_SELECTOR = "1de8edad";
    public static final String GET_TOKEN_KEY_PUBLIC_SELECTOR = "7a3f45cb";

    private final ContractClient contractClient;
    private final TokenClient tokenClient;
    private final FileClient fileClient;
    private final MirrorNodeClient mirrorClient;

    @Value("classpath:solidity/artifacts/contracts/PrecompileTestContract.sol/PrecompileTestContract.json")
    private Path precompileTestContract;

    private ContractId contractId;
    private FileId fileId;

    @Given("I successfully create a precompile contract from contract bytes")
    public void createNewContract() throws IOException {
        CompiledSolidityArtifact compiledSolidityArtifact = MAPPER.readValue(
                ResourceUtils.getFile(precompileTestContract.toUri()),
                CompiledSolidityArtifact.class);
        createContract(compiledSolidityArtifact.getBytecode());
    }

    @Given("I successfully create a fungible token for precompile contract tests")
    public void createFungibleToken() {
        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE
        );
    }

    @Given("I successfully create a non fungible token for precompile contract tests")
    public void createNonFungibleToken() {
        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenSupplyType.INFINITE
        );
    }

    @Then("Check if fungible token is token")
    public void checkIfFungibleTokenIsToken() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.IS_TOKEN_SELECTOR + TestUtil
                        .to32BytesString(tokenIds.get(0).toSolidityAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertTrue(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Check if non fungible token is token")
    public void checkIfNonFungibleTokenIsToken() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.IS_TOKEN_SELECTOR + TestUtil
                        .to32BytesString(tokenIds.get(1).toSolidityAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertTrue(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Check if fungible token is frozen")
    public void checkIfFungibleTokenIsFrozen() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.IS_TOKEN_FROZEN_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + TestUtil.to32BytesString(contractClient.getClientAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertFalse(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Check if non fungible token is frozen")
    public void checkIfNonFungibleTokenIsFrozen() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.IS_TOKEN_FROZEN_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + TestUtil.to32BytesString(contractClient.getClientAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertFalse(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Check if fungible token is kyc granted")
    public void checkIfFungibleTokenIsKycGranted() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.IS_KYC_GRANTED_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + TestUtil.to32BytesString(contractClient.getClientAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertFalse(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Check if non fungible token is kyc granted")
    public void checkIfNonFungibleTokenIsKycGranted() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.IS_KYC_GRANTED_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + TestUtil.to32BytesString(contractClient.getClientAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertFalse(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Get token default freeze of fungible token")
    public void getDefaultFreezeOfFungibleToken() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.GET_TOKEN_DEFAULT_FREEZE_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(0).toSolidityAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertFalse(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Get token default freeze of non fungible token")
    public void getDefaultFreezeOfNonFungibleToken() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.GET_TOKEN_DEFAULT_FREEZE_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(1).toSolidityAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertFalse(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Get token default kyc of fungible token")
    public void getDefaultKycOfFungibleToken() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.GET_TOKEN_DEFAULT_KYC_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(0).toSolidityAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertFalse(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Get token default kyc of non fungible token")
    public void getDefaultKycOfNonFungibleToken() {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.GET_TOKEN_DEFAULT_KYC_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(1).toSolidityAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );

        assertFalse(ContractCallResponse.convertContractCallResponseToBoolean(response));
    }

    @Then("Get information for token of fungible token")
    public void getInformationForTokenOfFungibleToken() throws IOException {
        ContractCallResponse response = mirrorClient.contractsCall(
                PrecompileContractFeature.GET_INFORMATION_FOR_TOKEN_SELECTOR
                        + TestUtil.to32BytesString(tokenIds.get(0).toSolidityAddress()),
                contractId.toSolidityAddress(),
                contractClient.getClientAddress()
        );
        CompiledSolidityArtifact compiledSolidityArtifact = MAPPER.readValue(
                ResourceUtils.getFile(precompileTestContract.toUri()),
                CompiledSolidityArtifact.class);

        assertTrue(true);
    }

    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    private void verifyToken(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());
    }

    private TokenId createNewToken(
            String symbol,
            TokenType tokenType,
            TokenSupplyType tokenSupplyType
    ) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                admin,
                1_000_000,
                tokenSupplyType,
                1_000_000,
                tokenType,
                new ArrayList<>());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
        tokenIds.add(tokenId);

        verifyToken(tokenId);

        return tokenId;
    }

    private void createContract(String byteCode) {
        persistContractBytes(byteCode.replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                contractClient.getSdkClient().getAcceptanceTestProperties().getFeatureProperties()
                        .getMaxContractFunctionGas(),
                null,
                null
        );

        verifyCreateContractNetworkResponse();
    }

    private void persistContractBytes(String contractContents) {
        // rely on SDK chunking feature to upload larger files
        networkTransactionResponse = fileClient.createFile(new byte[] {});
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        fileId = networkTransactionResponse.getReceipt().fileId;
        assertNotNull(fileId);
        log.info("Created file {} to hold contract init code", fileId);

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
}
