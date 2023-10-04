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
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.esaulpaugh.headlong.util.Strings;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.ContractCallResponse;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ERCContractFeature extends AbstractFeature {
    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final int MAX_SUPPLY = 1;

    private final AccountClient accountClient;
    private final List<TokenId> tokenIds = new CopyOnWriteArrayList<>();
    private final Map<TokenId, List<Long>> tokenSerialNumbers = new ConcurrentHashMap<>();
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;

    private ExpandedAccountId allowanceSpenderAccountId;
    private ExpandedAccountId spenderAccountId;
    private String spenderAccountAlias;
    private ExpandedAccountId spenderAccountIdForAllSerials;
    private ExpandedAccountId ecdsaAccount;
    private DeployedContract deployedErcContract;
    private String ercTestContractSolidityAddress;

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
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.NAME_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(asAddress(tokenIds.get(0).toSolidityAddress()));
        ContractCallResponse getNameResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getNameResponse.getResultAsText()).isEqualTo("TEST_name");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token symbol")
    public void symbolContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.SYMBOL_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(asAddress(tokenIds.get(0).toSolidityAddress()));
        ContractCallResponse getSymbolResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getSymbolResponse.getResultAsText()).isEqualTo("TEST");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token decimals")
    public void decimalsContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.DECIMALS_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(asAddress(tokenIds.get(0).toSolidityAddress()));
        ContractCallResponse getDecimalsResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getDecimalsResponse.getResultAsNumber()).isEqualTo(10L);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token totalSupply")
    public void totalSupplyContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.TOTAL_SUPPLY_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(asAddress(tokenIds.get(0).toSolidityAddress()));
        ContractCallResponse getTotalSupplyResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getTotalSupplyResponse.getResultAsNumber()).isEqualTo(1_000_000L);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token ownerOf")
    public void ownerOfContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.GET_OWNER_OF_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(asAddress(tokenIds.get(1).toSolidityAddress()), new BigInteger("1"));
        ContractCallResponse getOwnerOfResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);
        tokenClient.validateAddress(getOwnerOfResponse.getResultAsAddress());
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token tokenUri")
    public void tokenURIContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.TOKEN_URI_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(asAddress(tokenIds.get(1).toSolidityAddress()), new BigInteger("1"));
        ContractCallResponse getTokenURIResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getTokenURIResponse.getResultAsText()).isEqualTo("TEST_metadata");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token getApproved")
    public void getApprovedContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.GET_APPROVED_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(asAddress(tokenIds.get(1).toSolidityAddress()), new BigInteger("1"));
        ContractCallResponse getApprovedResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getApprovedResponse.getResultAsAddress()).isEqualTo("0000000000000000000000000000000000000000");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token allowance")
    public void allowanceContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.ALLOWANCE_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(
                        asAddress(tokenIds.get(0).toSolidityAddress()),
                        asAddress(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress()),
                        asAddress(contractClient.getClientAddress()));
        ContractCallResponse getAllowanceResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getAllowanceResponse.getResultAsNumber()).isZero();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token allowance with allowances")
    public void allowanceSecondContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.ALLOWANCE_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(
                        asAddress(tokenIds.get(0).toSolidityAddress()),
                        asAddress(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress()),
                        asAddress(allowanceSpenderAccountId.getAccountId().toSolidityAddress()));
        ContractCallResponse getAllowanceResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getAllowanceResponse.getResultAsNumber()).isEqualTo(2);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token isApprovedForAll")
    public void isApprovedForAllContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.IS_APPROVED_FOR_ALL_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(
                        asAddress(tokenIds.get(1).toSolidityAddress()),
                        asAddress(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress()),
                        asAddress(contractClient.getClientAddress()));
        ContractCallResponse getIsApproveForAllResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getIsApproveForAllResponse.getResultAsBoolean()).isFalse();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token isApprovedForAll with response true")
    public void isApprovedForAllSecondContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.IS_APPROVED_FOR_ALL_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(
                        asAddress(tokenIds.get(1).toSolidityAddress()),
                        asAddress(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress()),
                        asAddress(spenderAccountIdForAllSerials.getAccountId().toSolidityAddress()));
        ContractCallResponse getIsApproveForAllResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getIsApproveForAllResponse.getResultAsBoolean()).isTrue();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token balance")
    public void balanceOfContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.BALANCE_OF_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(
                        asAddress(tokenIds.get(0).toSolidityAddress()), asAddress(contractClient.getClientAddress()));
        ContractCallResponse getBalanceOfResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getBalanceOfResponse.getResultAsNumber()).isEqualTo(1000000);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token getApproved with response BOB")
    public void verifyNftAllowance() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.GET_APPROVED_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(asAddress(tokenIds.get(1).toSolidityAddress()), new BigInteger("1"));
        ContractCallResponse getApprovedResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);
        assertThat(getApprovedResponse.getResultAsAddress()).isEqualTo(spenderAccountAlias);
    }

    @Given("I successfully create an erc contract from contract bytes with balance 0")
    public void createNewContract() throws IOException {
        deployedErcContract = getContract(ContractResource.ERC_TEST_CONTRACT);
        ercTestContractSolidityAddress = deployedErcContract.contractId().toSolidityAddress();
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
        spenderAccountAlias = spenderAccountId.getPublicKey().toEvmAddress().toString();
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

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.IS_APPROVED_FOR_ALL_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(
                        asAddress(tokenIds.get(1).toSolidityAddress()),
                        asAddress(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress()),
                        asAddress(mirrorClient
                                .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                                .getEvmAddress()));
        ContractCallResponse getIsApproveForAllResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);
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
                ecdsaAccount.getPrivateKey(),
                500);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.ALLOWANCE_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(
                        asAddress(tokenIds.get(0).toSolidityAddress()),
                        asAddress(tokenClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .getAccountId()
                                .toSolidityAddress()),
                        asAddress(mirrorClient
                                .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                                .getEvmAddress()));
        ContractCallResponse getAllowanceResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

        assertThat(getAllowanceResponse.getResultAsNumber()).isEqualTo(1000);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token balance with alias account")
    public void balanceOfAliasAccountContractCall() {
        ByteBuffer encodedFunctionCall = getFunctionFromArtifact(
                        ContractMethods.BALANCE_OF_SELECTOR, ContractResource.ERC_TEST_CONTRACT)
                .encodeCallWithArgs(
                        asAddress(tokenIds.get(0).toSolidityAddress()),
                        asAddress(mirrorClient
                                .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                                .getEvmAddress()));
        ContractCallResponse getBalanceOfResponse =
                callContract(Strings.encode(encodedFunctionCall), ercTestContractSolidityAddress);

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

    @Getter
    @RequiredArgsConstructor
    private enum ContractMethods implements SelectorInterface {
        ALLOWANCE_SELECTOR("allowance"),
        BALANCE_OF_SELECTOR("balanceOf"),
        DECIMALS_SELECTOR("decimals"),
        GET_APPROVED_SELECTOR("getApproved"),
        GET_OWNER_OF_SELECTOR("getOwnerOf"),
        IS_APPROVED_FOR_ALL_SELECTOR("isApprovedForAll"),
        NAME_SELECTOR("name"),
        SYMBOL_SELECTOR("symbol"),
        TOKEN_URI_SELECTOR("tokenURI"),
        TOTAL_SUPPLY_SELECTOR("totalSupply");

        private final String selector;
    }
}
