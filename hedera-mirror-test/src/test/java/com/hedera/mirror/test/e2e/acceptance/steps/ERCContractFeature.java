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
import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ERC;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.ALLOWANCE_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.BALANCE_OF_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.DECIMALS_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.GET_APPROVED_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.GET_OWNER_OF_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.IS_APPROVED_FOR_ALL_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.NAME_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.SYMBOL_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.TOKEN_URI_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.steps.ERCContractFeature.ContractMethods.TOTAL_SUPPLY_SELECTOR;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hashgraph.sdk.NftId;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class ERCContractFeature extends AbstractFeature {
    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final int MAX_SUPPLY = 1;

    private final AccountClient accountClient;
    private final Map<TokenId, List<Long>> tokenSerialNumbers = new ConcurrentHashMap<>();
    private final MirrorNodeClient mirrorClient;
    private final TokenClient tokenClient;
    private ExpandedAccountId allowanceSpenderAccountId;
    private ExpandedAccountId spenderAccountId;
    private TokenId fungibleTokenId;
    private TokenId nonFungibleTokenId;
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
        var data = encodeData(ERC, NAME_SELECTOR, asAddress(fungibleTokenId));
        var getNameResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getNameResponse.getResultAsText()).isEqualTo(TokenNameEnum.FUNGIBLE_DELETABLE.getSymbol() + "_name");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token symbol")
    public void symbolContractCall() {
        var data = encodeData(ERC, SYMBOL_SELECTOR, asAddress(fungibleTokenId));
        var getSymbolResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getSymbolResponse.getResultAsText()).isEqualTo(TokenNameEnum.FUNGIBLE_DELETABLE.getSymbol());
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token decimals")
    public void decimalsContractCall() {
        var data = encodeData(ERC, DECIMALS_SELECTOR, asAddress(fungibleTokenId));
        var getDecimalsResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getDecimalsResponse.getResultAsNumber()).isEqualTo(10L);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token totalSupply")
    public void totalSupplyContractCall() {
        var data = encodeData(ERC, TOTAL_SUPPLY_SELECTOR, asAddress(fungibleTokenId));
        var getTotalSupplyResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getTotalSupplyResponse.getResultAsNumber()).isEqualTo(1_000_000L);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token ownerOf")
    public void ownerOfContractCall() {
        var data = encodeData(ERC, GET_OWNER_OF_SELECTOR, asAddress(nonFungibleTokenId), new BigInteger("1"));
        var getOwnerOfResponse = callContract(data, ercTestContractSolidityAddress);
        tokenClient.validateAddress(getOwnerOfResponse.getResultAsAddress());
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token tokenUri")
    public void tokenURIContractCall() {
        var data = encodeData(ERC, TOKEN_URI_SELECTOR, asAddress(nonFungibleTokenId), new BigInteger("1"));
        var getTokenURIResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getTokenURIResponse.getResultAsText()).isEqualTo("TEST_metadata");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token getApproved")
    public void getApprovedContractCall() {
        var data = encodeData(ERC, GET_APPROVED_SELECTOR, asAddress(nonFungibleTokenId), new BigInteger("1"));
        var getApprovedResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getApprovedResponse.getResultAsAddress()).isEqualTo("0000000000000000000000000000000000000000");
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token allowance")
    public void allowanceContractCall() {
        var data = encodeData(
                ERC, ALLOWANCE_SELECTOR, asAddress(fungibleTokenId), asAddress(tokenClient), asAddress(contractClient));
        var getAllowanceResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getAllowanceResponse.getResultAsNumber()).isZero();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token allowance with allowances")
    public void allowanceSecondContractCall() {
        var data = encodeData(
                ERC,
                ALLOWANCE_SELECTOR,
                asAddress(fungibleTokenId),
                asAddress(tokenClient),
                asAddress(allowanceSpenderAccountId));
        var getAllowanceResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getAllowanceResponse.getResultAsNumber()).isEqualTo(2);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token isApprovedForAll")
    public void isApprovedForAllContractCall() {
        var data = encodeData(
                ERC,
                IS_APPROVED_FOR_ALL_SELECTOR,
                asAddress(nonFungibleTokenId),
                asAddress(tokenClient),
                asAddress(contractClient));
        var getIsApproveForAllResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getIsApproveForAllResponse.getResultAsBoolean()).isFalse();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token isApprovedForAll with response true")
    public void isApprovedForAllSecondContractCall() {
        var data = encodeData(
                ERC,
                IS_APPROVED_FOR_ALL_SELECTOR,
                asAddress(nonFungibleTokenId),
                asAddress(tokenClient),
                asAddress(spenderAccountIdForAllSerials));
        var getIsApproveForAllResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getIsApproveForAllResponse.getResultAsBoolean()).isTrue();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token balance")
    public void balanceOfContractCall() {
        var data = encodeData(ERC, BALANCE_OF_SELECTOR, asAddress(fungibleTokenId), asAddress(contractClient));
        var getBalanceOfResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getBalanceOfResponse.getResultAsNumber()).isEqualTo(1000000);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token getApproved with response BOB")
    public void verifyNftAllowance() {
        var data = encodeData(ERC, GET_APPROVED_SELECTOR, asAddress(nonFungibleTokenId), new BigInteger("1"));
        var getApprovedResponse = callContract(data, ercTestContractSolidityAddress);
        assertThat(getApprovedResponse.getResultAsAddress()).isEqualTo(spenderAccountAlias);
    }

    @Given("I successfully create an erc contract from contract bytes with balance 0")
    public void createNewContract() throws IOException {
        deployedErcContract = getContract(ERC);
        ercTestContractSolidityAddress = deployedErcContract.contractId().toSolidityAddress();
    }

    @Then("I create a new token with freeze status 2 and kyc status 1")
    public void createNewFungibleToken() {
        final var tokenId =
                tokenClient.getToken(TokenNameEnum.FUNGIBLE_DELETABLE).tokenId();
        fungibleTokenId = tokenId;
    }

    @Then("I create a new nft with infinite supplyType")
    public void createNewNft() {
        final var tokenId = tokenClient.getToken(TokenNameEnum.NFT_DELETABLE).tokenId();
        tokenSerialNumbers.put(tokenId, new ArrayList<>());
        nonFungibleTokenId = tokenId;
    }

    @Then("I mint a serial number")
    public void mintNftToken() {
        networkTransactionResponse = tokenClient.mint(nonFungibleTokenId, "TEST_metadata".getBytes());
        assertNotNull(networkTransactionResponse.getTransactionId());
        TransactionReceipt receipt = networkTransactionResponse.getReceipt();
        assertNotNull(receipt);
        assertThat(receipt.serials.size()).isOne();
        long serialNumber = receipt.serials.get(0);
        assertThat(serialNumber).isPositive();
        tokenSerialNumbers.get(nonFungibleTokenId).add(serialNumber);
    }

    @Then("the mirror node REST API should return status {int} for the erc contract transaction")
    public void verifyMirrorAPIResponses(int status) {
        verifyMirrorTransactionsResponse(mirrorClient, status);
    }

    @Then("I approve {string} for nft")
    public void approveCryptoAllowance(String accountName) {
        var serial = tokenSerialNumbers.get(nonFungibleTokenId);
        var nftId = new NftId(nonFungibleTokenId, serial.get(0));

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
                accountClient.approveToken(fungibleTokenId, allowanceSpenderAccountId.getAccountId(), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("I approve {string} for nft all serials")
    public void approveCryptoAllowanceAllSerials(String accountName) {
        spenderAccountIdForAllSerials = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        networkTransactionResponse =
                accountClient.approveNftAllSerials(nonFungibleTokenId, spenderAccountIdForAllSerials.getAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @RetryAsserts
    @Then(
            "I call the erc contract via the mirror node REST API for token isApprovedForAll with response true with alias accounts")
    public void isApprovedForAllWithAliasSecondContractCall() {
        ecdsaAccount = accountClient.getAccount(BOB);
        tokenClient.associate(ecdsaAccount, nonFungibleTokenId);
        networkTransactionResponse =
                accountClient.approveNftAllSerials(nonFungibleTokenId, ecdsaAccount.getAccountId());
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var data = encodeData(
                ERC,
                IS_APPROVED_FOR_ALL_SELECTOR,
                asAddress(nonFungibleTokenId),
                asAddress(tokenClient),
                asAddress(mirrorClient
                        .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                        .getEvmAddress()));
        var getIsApproveForAllResponse = callContract(data, ercTestContractSolidityAddress);
        assertThat(getIsApproveForAllResponse.getResultAsBoolean()).isTrue();
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token allowance with alias accounts")
    public void allowanceAliasAccountsCall() {
        tokenClient.associate(ecdsaAccount, fungibleTokenId);
        accountClient.approveToken(fungibleTokenId, ecdsaAccount.getAccountId(), 1_000);
        networkTransactionResponse = tokenClient.transferFungibleToken(
                fungibleTokenId,
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                ecdsaAccount.getAccountId(),
                ecdsaAccount.getPrivateKey(),
                500);
        verifyMirrorTransactionsResponse(mirrorClient, 200);

        var data = encodeData(
                ERC,
                ALLOWANCE_SELECTOR,
                asAddress(fungibleTokenId),
                asAddress(tokenClient),
                asAddress(mirrorClient
                        .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                        .getEvmAddress()));
        var getAllowanceResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getAllowanceResponse.getResultAsNumber()).isEqualTo(1000);
    }

    @RetryAsserts
    @Then("I call the erc contract via the mirror node REST API for token balance with alias account")
    public void balanceOfAliasAccountContractCall() {
        var data = encodeData(
                ERC,
                BALANCE_OF_SELECTOR,
                asAddress(fungibleTokenId),
                asAddress(mirrorClient
                        .getAccountDetailsByAccountId(ecdsaAccount.getAccountId())
                        .getEvmAddress()));
        var getBalanceOfResponse = callContract(data, ercTestContractSolidityAddress);

        assertThat(getBalanceOfResponse.getResultAsNumber()).isEqualTo(500);
    }

    @Getter
    @RequiredArgsConstructor
    enum ContractMethods implements SelectorInterface {
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
