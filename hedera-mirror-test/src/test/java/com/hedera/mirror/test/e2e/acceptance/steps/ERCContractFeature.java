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

import static com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum.BOB;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.to32BytesString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hedera.hashgraph.sdk.ContractId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient;
import com.hedera.mirror.test.e2e.acceptance.client.FileClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.CompiledSolidityArtifact;
import com.hedera.mirror.test.e2e.acceptance.props.ContractCallRequest;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ERCContractFeature extends AbstractFeature {
    private static final String ALLOWANCE_SELECTOR = "927da105";
    private static final String BALANCE_OF_SELECTOR = "f7888aec";
    private static final String DECIMALS_SELECTOR = "d449a832";
    private static final String GET_APPROVED_SELECTOR = "098f2366";
    private static final String GET_OWNER_OF_SELECTOR = "d5d03e21";
    private static final String IS_APPROVED_FOR_ALL_SELECTOR = "f49f40db";
    private static final String NAME_SELECTOR = "01984892";
    private static final String SYMBOL_SELECTOR = "a86e3576";
    private static final String TOKEN_URI_SELECTOR = "e9dc6375";
    private static final String TOTAL_SUPPLY_SELECTOR = "e4dc2aa4";
    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final int MAX_SUPPLY = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private final AccountClient accountClient;
    private final ContractClient contractClient;
    private final FileClient fileClient;
    private final List<TokenId> tokenIds = new CopyOnWriteArrayList<>();
    private final Map<TokenId, List<Long>> tokenSerialNumbers = new ConcurrentHashMap<>();
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;

    private CompiledSolidityArtifact compiledSolidityArtifact;
    private ContractId contractId;
    private ExpandedAccountId allowanceSpenderAccountId;
    private ExpandedAccountId spenderAccountId;
    private ExpandedAccountId spenderAccountIdForAllSerials;
    private ExpandedAccountId ecdsaAccount;
    private FileId fileId;

    @Value("classpath:solidity/artifacts/contracts/ERCTestContract.sol/ERCTestContract.json")
    private Resource ercContract;

    @After
    public void clean() {
        if (contractId != null) {
            contractClient.deleteContract(
                    contractId,
                    contractClient.getSdkClient().getExpandedOperatorAccountId().getAccountId(),
                    null);
        }

        tokenSerialNumbers.clear();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token name")
    public void nameContractCall() {
        var contractCallGetName = ContractCallRequest.builder()
                .data(NAME_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getNameResponse = mirrorClient.contractsCall(contractCallGetName);

        assertThat(getNameResponse.getResultAsText()).isEqualTo("TEST_name");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token symbol")
    public void symbolContractCall() {
        var contractCallGetSymbol = ContractCallRequest.builder()
                .data(SYMBOL_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getSymbolResponse = mirrorClient.contractsCall(contractCallGetSymbol);

        assertThat(getSymbolResponse.getResultAsText()).isEqualTo("TEST");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token decimals")
    public void decimalsContractCall() {
        var contractCallGetDecimals = ContractCallRequest.builder()
                .data(DECIMALS_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getDecimalsResponse = mirrorClient.contractsCall(contractCallGetDecimals);

        assertThat(getDecimalsResponse.getResultAsNumber()).isEqualTo(10L);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token totalSupply")
    public void totalSupplyContractCall() {
        var contractCallGetTotalSupply = ContractCallRequest.builder()
                .data(TOTAL_SUPPLY_SELECTOR + to32BytesString(tokenIds.get(0).toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getTotalSupplyResponse = mirrorClient.contractsCall(contractCallGetTotalSupply);

        assertThat(getTotalSupplyResponse.getResultAsNumber()).isEqualTo(1_000_000L);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token ownerOf")
    public void ownerOfContractCall() {
        var contractCallGetOwnerOf = ContractCallRequest.builder()
                .data(GET_OWNER_OF_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString("1"))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getOwnerOfResponse = mirrorClient.contractsCall(contractCallGetOwnerOf);
        tokenClient.validateAddress(getOwnerOfResponse.getResultAsAddress());
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token tokenUri")
    public void tokenURIContractCall() {
        var contractCallGetTokenURI = ContractCallRequest.builder()
                .data(TOKEN_URI_SELECTOR + to32BytesString(tokenIds.get(1).toSolidityAddress()) + to32BytesString("1"))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getTokenURIResponse = mirrorClient.contractsCall(contractCallGetTokenURI);

        assertThat(getTokenURIResponse.getResultAsText()).isEqualTo("TEST_metadata");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token getApproved")
    public void getApprovedContractCall() {
        var contractCallGetApproved = ContractCallRequest.builder()
                .data(GET_APPROVED_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString("1"))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        var getApprovedResponse = mirrorClient.contractsCall(contractCallGetApproved);

        assertThat(getApprovedResponse.getResultAsAddress()).isEqualTo("0000000000000000000000000000000000000000");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token allowance")
    public void allowanceContractCall() {
        var contractCallGetAllowance = ContractCallRequest.builder()
                .data(ALLOWANCE_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();

        var getAllowanceResponse = mirrorClient.contractsCall(contractCallGetAllowance);

        assertThat(getAllowanceResponse.getResultAsNumber()).isZero();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token allowance with allowances")
    public void allowanceSecondContractCall() {
        var contractCallGetAllowance = ContractCallRequest.builder()
                .data(ALLOWANCE_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress())
                        + to32BytesString(
                                allowanceSpenderAccountId.getAccountId().toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getAllowanceResponse = mirrorClient.contractsCall(contractCallGetAllowance);

        assertThat(getAllowanceResponse.getResultAsNumber()).isEqualTo(2);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token isApprovedForAll")
    public void isApprovedForAllContractCall() {
        var contractCallGetIsApproveForAll = ContractCallRequest.builder()
                .data(IS_APPROVED_FOR_ALL_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getIsApproveForAllResponse = mirrorClient.contractsCall(contractCallGetIsApproveForAll);

        assertThat(getIsApproveForAllResponse.getResultAsBoolean()).isFalse();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token isApprovedForAll with response true")
    public void isApprovedForAllSecondContractCall() {
        var contractCallGetIsApproveForAll = ContractCallRequest.builder()
                .data(IS_APPROVED_FOR_ALL_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress())
                        + to32BytesString(
                                spenderAccountIdForAllSerials.getAccountId().toSolidityAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getIsApproveForAllResponse = mirrorClient.contractsCall(contractCallGetIsApproveForAll);

        assertThat(getIsApproveForAllResponse.getResultAsBoolean()).isTrue();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token balance")
    public void balanceOfContractCall() {
        var contractCallGetBalanceOf = ContractCallRequest.builder()
                .data(BALANCE_OF_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(contractClient.getClientAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getBalanceOfResponse = mirrorClient.contractsCall(contractCallGetBalanceOf);

        assertThat(getBalanceOfResponse.getResultAsNumber()).isEqualTo(1000000);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token getApproved with response BOB")
    public void verifyNftAllowance() {
        var from = contractClient.getClientAddress();
        var to = contractId.toSolidityAddress();
        var nft = to32BytesString(tokenIds.get(1).toSolidityAddress());

        var contractCallGetApproved = ContractCallRequest.builder()
                .data(GET_APPROVED_SELECTOR + nft + to32BytesString("1"))
                .from(from)
                .to(to)
                .estimate(false)
                .build();

        var getApprovedResponse = mirrorClient.contractsCall(contractCallGetApproved);
        assertThat(getApprovedResponse.getResultAsAddress())
                .isEqualTo(spenderAccountId.getAccountId().toSolidityAddress());
    }

    @Given("I successfully create an erc contract from contract bytes with balance 0")
    public void createNewContract() throws IOException {
        try (var in = ercContract.getInputStream()) {
            compiledSolidityArtifact = MAPPER.readValue(in, CompiledSolidityArtifact.class);
            createContract(compiledSolidityArtifact.getBytecode(), 0);
        }
    }

    @Then("I create a new token with freeze status 2 and kyc status 1")
    public void createNewFungibleToken() {
        createNewToken(
                "TEST",
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                Collections.emptyList());
    }

    @Then("I create a new nft with supplyType {string}")
    public void createNewNft(String tokenSupplyType) {
        TokenId tokenId = createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenSupplyType.valueOf(tokenSupplyType),
                Collections.emptyList());
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

    @Then("the mirror node REST API should return status {int} for the erc contract transaction")
    public void verifyMirrorAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Then("I approve {string} for nft")
    public void approveCryptoAllowance(String accountName) {
        var serial = tokenSerialNumbers.get(tokenIds.get(1));
        var nftId = new NftId(tokenIds.get(1), serial.get(0));

        spenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        networkTransactionResponse = accountClient.approveNft(nftId, spenderAccountId.getAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("I approve {string} with {long}")
    public void approveTokenAllowance(String accountName, long amount) {
        allowanceSpenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        networkTransactionResponse =
                accountClient.approveToken(tokenIds.get(0), allowanceSpenderAccountId.getAccountId(), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("I approve {string} for nft all serials")
    public void approveCryptoAllowanceAllSerials(String accountName) {
        spenderAccountIdForAllSerials = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        networkTransactionResponse =
                accountClient.approveNftAllSerials(tokenIds.get(1), spenderAccountIdForAllSerials.getAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @RetryAsserts
    @Then(
            "I call the erc contract via the mirror node REST API for token isApprovedForAll with response true with alias accounts")
    public void isApprovedForAllWithAliasSecondContractCall() {
        ecdsaAccount = accountClient.getAccount(BOB);
        tokenClient.associate(ecdsaAccount, tokenIds.get(1));
        networkTransactionResponse = accountClient.approveNftAllSerials(tokenIds.get(1), ecdsaAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var contractCallGetIsApproveForAll = ContractCallRequest.builder()
                .data(IS_APPROVED_FOR_ALL_SELECTOR
                        + to32BytesString(tokenIds.get(1).toSolidityAddress())
                        + to32BytesString(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress())
                        + to32BytesString(mirrorClient
                                .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                                .getEvmAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getIsApproveForAllResponse = mirrorClient.contractsCall(contractCallGetIsApproveForAll);
        assertThat(getIsApproveForAllResponse.getResultAsBoolean()).isTrue();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token allowance with alias accounts")
    public void allowanceAliasAccountsCall() {
        tokenClient.associate(ecdsaAccount, tokenIds.get(0));
        accountClient.approveToken(tokenIds.get(0), ecdsaAccount.getAccountId(), 1_000);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                tokenIds.get(0),
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                ecdsaAccount.getAccountId(),
                500);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var contractCallGetAllowance = ContractCallRequest.builder()
                .data(ALLOWANCE_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress())
                        + to32BytesString(mirrorClient
                                .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                                .getEvmAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getAllowanceResponse = mirrorClient.contractsCall(contractCallGetAllowance);

        assertThat(getAllowanceResponse.getResultAsNumber()).isEqualTo(1000);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token balance with alias account")
    public void balanceOfAliasAccountContractCall() {
        var contractCallGetBalanceOf = ContractCallRequest.builder()
                .data(BALANCE_OF_SELECTOR
                        + to32BytesString(tokenIds.get(0).toSolidityAddress())
                        + to32BytesString(mirrorClient
                                .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                                .getEvmAddress()))
                .from(contractClient.getClientAddress())
                .to(contractId.toSolidityAddress())
                .estimate(false)
                .build();
        var getBalanceOfResponse = mirrorClient.contractsCall(contractCallGetBalanceOf);

        assertThat(getBalanceOfResponse.getResultAsNumber()).isEqualTo(500);
    }

    private TokenId createNewToken(
            String symbol,
            int freezeStatus,
            int kycStatus,
            TokenType tokenType,
            TokenSupplyType tokenSupplyType,
            List<CustomFee> customFees) {
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

        return tokenId;
    }

    private void createContract(String byteCode, int initialBalance) {
        persistContractBytes(byteCode.replaceFirst("0x", ""));
        networkTransactionResponse = contractClient.createContract(
                fileId,
                contractClient
                        .getSdkClient()
                        .getAcceptanceTestProperties()
                        .getFeatureProperties()
                        .getMaxContractFunctionGas(),
                initialBalance == 0 ? null : Hbar.fromTinybars(initialBalance),
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
}
