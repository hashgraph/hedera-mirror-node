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

import static com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse.convertContractCallResponseToAddress;
import static com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse.convertContractCallResponseToBoolean;
import static com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse.convertContractCallResponseToNum;
import static com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse.hexToASCII;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.ResourceUtils;

import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;

@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ERCContractFeature extends AbstractFeature {

    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final int MAX_SUPPLY = 1;

    private final List<TokenId> tokenIds = new ArrayList<>();
    private final Map<TokenId, List<Long>> tokenSerialNumbers = new HashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public static final String NAME_SELECTOR = "01984892";
    public static final String SYMBOL_SELECTOR = "a86e3576";
    public static final String DECIMALS_SELECTOR = "d449a832";
    public static final String TOTAL_SUPPLY_SELECTOR = "e4dc2aa4";
    public static final String BALANCE_OF_SELECTOR = "f7888aec";
    public static final String ALLOWANCE_SELECTOR = "927da105";
    public static final String GET_APPROVED_SELECTOR = "098f2366";
    public static final String IS_APPROVED_FOR_ALL_SELECTOR = "f49f40db";
    public static final String GET_OWNER_OF_SELECTOR = "d5d03e21";
    public static final String TOKEN_URI_SELECTOR = "e9dc6375";

    private ExpandedAccountId spenderAccountId;
    private ExpandedAccountId spenderAccountIdForAllSeerials;
    private ExpandedAccountId allowanceSpenderAccountId;

    private final ContractClient contractClient;
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private final ContractFeature contractFeature;
    private final TokenFeature tokenFeature;
    private final AccountFeature accountFeature;

    @Value("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json")
    private Path ercContract;

    private ContractId contractId;
    private CompiledSolidityArtifact compiledSolidityArtifact;

    @Then("I call the erc contract via the mirror node REST API for token name")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void nameContractCall() throws DecoderException {
        var getNameResponse = mirrorClient.contractsCall(NAME_SELECTOR
                + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(hexToASCII(getNameResponse.getResult())).isEqualTo("TEST_name");
    }

    @Then("I call the erc contract via the mirror node REST API for token symbol")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void symbolContractCall() throws DecoderException {
        var getSymbolResponse = mirrorClient.contractsCall(SYMBOL_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(hexToASCII(getSymbolResponse.getResult())).isEqualTo("TEST");
    }

    @Then("I call the erc contract via the mirror node REST API for token decimals")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void decimalsContractCall() {
        var getDecimalsResponse = mirrorClient.contractsCall(DECIMALS_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToNum(getDecimalsResponse)).isEqualTo(10L);
    }

    @Then("I call the erc contract via the mirror node REST API for token totalSupply")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void totalSupplyContractCall() {
        var getTotalSupplyResponse = mirrorClient.contractsCall(TOTAL_SUPPLY_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress()),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToNum(getTotalSupplyResponse)).isEqualTo(1_000_000L);
    }

    @Then("I call the erc contract via the mirror node REST API for token ownerOf")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void ownerOfContractCall() {
        var getOwnerOfResponse = mirrorClient.contractsCall(GET_OWNER_OF_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress()) + to32BytesString("1"),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToAddress(getOwnerOfResponse))
                .isEqualTo(tokenClient.getSdkClient().getExpandedOperatorAccountId().getAccountId().toSolidityAddress());
    }

    @Then("I call the erc contract via the mirror node REST API for token tokenUri")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void tokenURIContractCall() throws DecoderException {
        var getTokenURIResponse = mirrorClient.contractsCall(TOKEN_URI_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress()) + to32BytesString("1"),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(hexToASCII(getTokenURIResponse.getResult())).isEqualTo("TEST_metadata");
    }

    @Then("I call the erc contract via the mirror node REST API for token getApproved")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void getApprovedContractCall() {
        var getApprovedResponse = mirrorClient.contractsCall(GET_APPROVED_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress()) + to32BytesString("1"),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToAddress(getApprovedResponse))
                .isEqualTo("0000000000000000000000000000000000000000");
    }

    @Then("I call the erc contract via the mirror node REST API for token allowance")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void allowanceContractCall() {
        var getAllowanceResponse = mirrorClient.contractsCall(ALLOWANCE_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(tokenClient.getSdkClient().getExpandedOperatorAccountId().getAccountId().toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToNum(getAllowanceResponse)).isZero();
    }

    @Then("I call the erc contract via the mirror node REST API for token allowance with allowances")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void allowanceSecondContractCall() {
        var getAllowanceResponse = mirrorClient.contractsCall(ALLOWANCE_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(tokenClient.getSdkClient().getExpandedOperatorAccountId().getAccountId().toSolidityAddress())
                        + to32BytesString(allowanceSpenderAccountId.getAccountId().toSolidityAddress()),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToNum(getAllowanceResponse)).isEqualTo(2);
    }

    @Then("I call the erc contract via the mirror node REST API for token isApprovedForAll")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void isApprovedForAllContractCall() throws DecoderException {
        var getIsApproveForAllResponse = mirrorClient.contractsCall(IS_APPROVED_FOR_ALL_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(tokenClient.getSdkClient().getExpandedOperatorAccountId().getAccountId().toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToBoolean(getIsApproveForAllResponse)).isFalse();
    }

    @Then("I call the erc contract via the mirror node REST API for token isApprovedForAll with response true")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void isApprovedForAllSecondContractCall() {
        var getIsApproveForAllResponse = mirrorClient.contractsCall(IS_APPROVED_FOR_ALL_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(tokenClient.getSdkClient().getExpandedOperatorAccountId().getAccountId().toSolidityAddress())
                        + to32BytesString(spenderAccountIdForAllSeerials.getAccountId().toSolidityAddress()),
                contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToBoolean(getIsApproveForAllResponse)).isTrue();
    }

    @Then("I call the erc contract via the mirror node REST API for token balance")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void balanceOfContractCall() {
        var getBalanceOfResponse = mirrorClient.contractsCall(BALANCE_OF_SELECTOR
                + to32BytesString(tokenIds.get(0).toSolidityAddress())
                + to32BytesString(contractClient.getClientAddress()), contractId.toSolidityAddress(), contractClient.getClientAddress());

        assertThat(convertContractCallResponseToNum(getBalanceOfResponse)).isZero();
    }

    @Then("I call the erc contract via the mirror node REST API for token getApproved with response BOB")
    @Retryable(value = {AssertionError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyNftAllowance() {
        var from = contractClient.getClientAddress();
        var to = contractId.toSolidityAddress();
        var nft = to32BytesString(tokenIds.get(1).toSolidityAddress());

        var getApprovedResponse = mirrorClient.contractsCall(GET_APPROVED_SELECTOR + nft +
                to32BytesString("1"), to, from);
        assertThat(convertContractCallResponseToAddress(getApprovedResponse))
                .isEqualTo(spenderAccountId.getAccountId().toSolidityAddress());
    }

    @Given("I successfully create an erc contract from contract bytes with balance 0")
    public void createNewContract() throws IOException {
        compiledSolidityArtifact = MAPPER.readValue(
                ResourceUtils.getFile(ercContract.toUri()),
                CompiledSolidityArtifact.class);
        contractId = contractFeature.createContract(compiledSolidityArtifact.getBytecode(), 0);
    }

    @Then("I create a new token with freeze status 2 and kyc status 1")
    public void createNewToken() {
        createNewToken("TEST", TokenFreezeStatus.FreezeNotApplicable_VALUE, TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Then("I create a new nft with supplyType {string}")
    public void createNewNft(String tokenSupplyType) {
        TokenId tokenId = tokenFeature.createNewNft(RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenSupplyType.valueOf(tokenSupplyType));
        tokenIds.add(tokenId);
        tokenSerialNumbers.put(tokenId, new ArrayList<>());
    }

    @Then("I mint a serial number")
    public void mintNftToken() {
        TokenId tokenId = tokenIds.get(1);
        networkTransactionResponse = tokenClient.mint(tokenId, "TEST_metadata".getBytes());
        assertNotNull(networkTransactionResponse.getTransactionId());
        TransactionReceipt receipt = networkTransactionResponse.getReceipt();
        assertNotNull(receipt);
        assertThat(receipt.serials.size()).isOne();
        long serialNumber = receipt.serials.get(0);
        assertThat(serialNumber).isPositive();
        tokenSerialNumbers.get(tokenId).add(serialNumber);
    }

    @Then("I approve {string} for nft")
    public void approveCryptoAllowance(String accountName) {
        var serial = tokenSerialNumbers.get(tokenIds.get(1));
        spenderAccountId = accountFeature.setNftAllowance(accountName, new NftId(tokenIds.get(1), serial.get(0)));
    }

    @Then("I approve {string} with {long}")
    public void approveTokenAllowance(String accountName, long amount) {
        allowanceSpenderAccountId = accountFeature.setTokenAllowance(accountName, tokenIds.get(0), amount);
    }

    @Then("I approve {string} for nft all serials")
    public void approveCryptoAllowanceAllSerials(String accountName) {
        spenderAccountIdForAllSeerials = accountFeature.setNftAllowanceAllSerials(accountName, tokenIds.get(1));
    }


    public void createNewToken(String symbol, int freezeStatus, int kycStatus) {
        createNewToken(symbol, freezeStatus, kycStatus, TokenType.FUNGIBLE_COMMON, TokenSupplyType.INFINITE, Collections
                .emptyList());
    }

    private void createNewToken(String symbol, int freezeStatus, int kycStatus, TokenType tokenType,
            TokenSupplyType tokenSupplyType, List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                freezeStatus,
                kycStatus,
                admin,
                INITIAL_SUPPLY,
                tokenSupplyType,
                MAX_SUPPLY,
                tokenType,
                customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
        tokenIds.add(tokenId);
    }
}
