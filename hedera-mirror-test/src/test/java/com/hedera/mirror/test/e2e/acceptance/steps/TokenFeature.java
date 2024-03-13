/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hashgraph.sdk.Status.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hedera.mirror.rest.model.TransactionTypes.CRYPTOTRANSFER;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.nextBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.rest.model.AssessedCustomFee;
import com.hedera.mirror.rest.model.CustomFees;
import com.hedera.mirror.rest.model.FixedFee;
import com.hedera.mirror.rest.model.FractionalFee;
import com.hedera.mirror.rest.model.FractionalFeeAmount;
import com.hedera.mirror.rest.model.Nft;
import com.hedera.mirror.rest.model.NftTransactionHistory;
import com.hedera.mirror.rest.model.NftTransactionTransfer;
import com.hedera.mirror.rest.model.TokenAllowance;
import com.hedera.mirror.rest.model.TokenInfo;
import com.hedera.mirror.rest.model.TokenInfo.PauseStatusEnum;
import com.hedera.mirror.rest.model.TokenRelationship;
import com.hedera.mirror.rest.model.TokenRelationship.FreezeStatusEnum;
import com.hedera.mirror.rest.model.TokenRelationship.KycStatusEnum;
import com.hedera.mirror.rest.model.TokenRelationshipResponse;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import com.hedera.mirror.rest.model.TransactionDetail;
import com.hedera.mirror.rest.model.TransactionNftTransfersInner;
import com.hedera.mirror.rest.model.TransactionTokenTransfersInner;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

@CustomLog
@RequiredArgsConstructor
public class TokenFeature extends AbstractFeature {

    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final int MAX_SUPPLY = 1;

    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;

    private final Map<TokenId, List<Long>> tokenSerialNumbers = new HashMap<>();

    private List<CustomFee> customFees = List.of();
    private TokenId tokenId;

    public TokenId getTokenId() {
        return tokenId;
    }

    @Given("I ensure token {token} has been created")
    public void createNamedToken(TokenNameEnum tokenName) {
        var tokenAndResponse = tokenClient.getToken(tokenName);
        if (tokenAndResponse.response() != null) {
            this.networkTransactionResponse = tokenAndResponse.response();
            verifyMirrorTransactionsResponse(mirrorClient, 200);
        }
        var tokenInfo = mirrorClient.getTokenInfo(tokenAndResponse.tokenId().toString());
        log.debug("Get token info for token {}: {}", tokenName, tokenInfo);
    }

    @Given("I ensure token has the correct properties")
    public void ensureTokenProperties() {
        var tokensResponse = mirrorClient.getTokens(tokenId.toString()).getTokens();
        assertThat(tokensResponse).isNotNull().hasSize(1);
        var token = tokensResponse.get(0);
        var tokenDecimals = token.getDecimals();
        if (token.getType().equals(TokenType.NON_FUNGIBLE_UNIQUE.toString())) {
            assertThat(tokenDecimals).isZero();
        } else {
            assertThat(tokenDecimals).isEqualTo(10L);
        }
        assertThat(token.getName()).isNotNull();
        log.debug("Get tokens response for token {}: {}", tokenId, tokensResponse);

        var balancesResponse = mirrorClient.getTokenBalances(tokenId.toString()).getBalances();
        assertThat(balancesResponse).isNotNull().hasSize(1);
        var balanceDecimals = balancesResponse.get(0).getDecimals();
        assertThat(balanceDecimals).isEqualTo(tokenDecimals);
        log.debug("Get token balances for token {}: {}", tokenId, balancesResponse);
    }

    @Given("I associate account {account} with token {token}")
    public void associateToken(AccountNameEnum accountName, TokenNameEnum tokenName) {
        var accountId = accountClient.getAccount(accountName);
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        associateWithToken(accountId, tokenId);
    }

    @Given("I approve {account} to transfer up to {long} of token {token}")
    public void setFungibleTokenAllowance(AccountNameEnum accountName, long amount, TokenNameEnum tokenName) {
        var spenderAccountId = accountClient.getAccount(accountName).getAccountId();
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        networkTransactionResponse = accountClient.approveToken(tokenId, spenderAccountId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should confirm the approved allowance {long} of {token} for {account}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenAllowanceResponse(
            long approvedAmount, TokenNameEnum tokenName, AccountNameEnum accountName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var spenderAccountId = accountClient.getAccount(accountName);
        verifyMirrorAPIApprovedTokenAllowanceResponse(tokenId, spenderAccountId, approvedAmount, 0);
    }

    @Then("the mirror node REST API should confirm the approved allowance for {token} and {account} no longer exists")
    @RetryAsserts
    public void verifyTokenAllowanceDelete(TokenNameEnum tokenName, AccountNameEnum accountName) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var spender = accountClient.getAccount(accountName).getAccountId().toString();
        var token = tokenClient.getToken(tokenName).tokenId().toString();

        var mirrorTokenAllowanceResponse = mirrorClient.getAccountTokenAllowanceBySpender(owner, token, spender);
        assertThat(mirrorTokenAllowanceResponse.getAllowances()).isEmpty();
    }

    @Then("the mirror node REST API should confirm the debit of {long} from {token} allowance of {long} for {account}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedDebitedTokenAllowanceResponse(
            long debitAmount, TokenNameEnum tokenName, long approvedAmount, AccountNameEnum accountName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var spenderAccountId = accountClient.getAccount(accountName);
        verifyMirrorAPIApprovedTokenAllowanceResponse(tokenId, spenderAccountId, approvedAmount, debitAmount);
    }

    @Then("I transfer {long} of token {token} to {account}")
    public void transferTokensToRecipient(long amount, TokenNameEnum tokenName, AccountNameEnum accountName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var recipientAccountId = accountClient.getAccount(accountName).getAccountId();
        var ownerAccountId = tokenClient.getSdkClient().getExpandedOperatorAccountId();

        networkTransactionResponse = tokenClient.transferFungibleToken(
                tokenId, ownerAccountId.getAccountId(), ownerAccountId, recipientAccountId, amount, false, null);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("{account} transfers {long} of token {token} to {account}")
    public void transferFromAllowance(
            AccountNameEnum spender, long amount, TokenNameEnum token, AccountNameEnum recipient) {
        var tokenId = tokenClient.getToken(token).tokenId();
        var spenderAccountId = accountClient.getAccount(spender);
        var recipientAccountId = accountClient.getAccount(recipient).getAccountId();
        var ownerAccountId = accountClient.getClient().getOperatorAccountId();

        networkTransactionResponse = tokenClient.transferFungibleToken(
                tokenId, ownerAccountId, spenderAccountId, recipientAccountId, amount, true, null);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should confirm the transfer of {long} {token}")
    @RetryAsserts
    public void verifyMirrorAPITokenTransferResponse(long transferAmount, TokenNameEnum tokenName) {
        verifyMirrorAPIApprovedTokenTransferResponse(transferAmount, tokenName, false);
    }

    @Then("the mirror node REST API should confirm the approved transfer of {long} {token}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenTransferResponse(long transferAmount, TokenNameEnum tokenName) {
        verifyMirrorAPIApprovedTokenTransferResponse(transferAmount, tokenName, true);
    }

    private void verifyMirrorAPIApprovedTokenTransferResponse(
            long transferAmount, TokenNameEnum tokenName, boolean isApproval) {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        var tokenId = tokenClient.getToken(tokenName).tokenId().toString();
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var expectedTokenTransfer = new TransactionTokenTransfersInner()
                .tokenId(tokenId)
                .account(owner)
                .amount(-transferAmount)
                .isApproval(isApproval);
        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).hasSize(1).first().satisfies(t -> assertThat(t.getTokenTransfers())
                .contains(expectedTokenTransfer));
    }

    @Given("I delete the allowance on token {token} for {account}")
    public void setFungibleTokenAllowance(TokenNameEnum tokenName, AccountNameEnum spenderAccountName) {
        var tokenId = tokenClient.getToken(tokenName).tokenId();
        var spenderAccountId = accountClient.getAccount(spenderAccountName);
        networkTransactionResponse = accountClient.approveToken(tokenId, spenderAccountId.getAccountId(), 0L);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully create a new token with custom fees schedule")
    public void createNewToken(List<CustomFee> customFees) {
        final var result = tokenClient.getToken(TokenNameEnum.FUNGIBLE_WITH_CUSTOM_FEES, customFees);
        this.tokenId = result.tokenId();
        this.networkTransactionResponse = result.response();
        this.customFees = customFees;
    }

    @Given("I successfully create a new unfrozen and granted kyc token")
    public void createNewToken() {
        final var response = tokenClient.getToken(TokenNameEnum.FUNGIBLE_KYC_UNFROZEN_2);
        this.tokenId = response.tokenId();
        this.networkTransactionResponse = response.response();
    }

    @Given("I successfully create a new nft with infinite supplyType")
    public void createNewNft() {
        final var result = tokenClient.getToken(TokenNameEnum.NFT_DELETABLE);
        this.networkTransactionResponse = result.response();
        this.tokenId = result.tokenId();
        tokenSerialNumbers.put(tokenId, new ArrayList<>());
    }

    @Given("I associate {account} with token")
    public void associateWithToken(AccountNameEnum accountName) {
        var accountId = accountClient.getAccount(accountName);
        associateWithToken(accountId, tokenId);
    }

    @When("I set account freeze status to {int}")
    public void setFreezeStatus(int freezeStatus) {
        setFreezeStatus(freezeStatus, getRecipientAccountId());
    }

    @When("I set account kyc status to {int}")
    public void setKycStatus(int kycStatus) {
        setKycStatus(kycStatus, getRecipientAccountId());
    }

    @Then("I transfer {int} tokens to {account}")
    public void transferTokensToRecipient(int amount, AccountNameEnum accountName) {
        transferTokens(tokenId, amount, null, accountName);
    }

    @Then("I transfer serial number {int} to {account}")
    public void transferNftsToRecipient(int serialNumberIndex, AccountNameEnum accountName) {
        ExpandedAccountId payer = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        transferNfts(
                tokenId, tokenSerialNumbers.get(tokenId).get(getIndexOrDefault(serialNumberIndex)), payer, accountName);
    }

    @Then("{account} transfers {int} tokens to {account} with fractional fee {int}")
    public void transferTokensFromSenderToRecipientWithFee(
            AccountNameEnum sender, int amount, AccountNameEnum accountName, int fractionalFee) {
        transferTokens(tokenId, amount, sender, accountName, fractionalFee);
    }

    @Given("I update the token")
    public void updateToken() {
        var operatorId = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.updateToken(tokenId, operatorId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I pause the token")
    public void pauseToken() {
        networkTransactionResponse = tokenClient.pause(tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I unpause the token")
    public void unpauseToken() {
        networkTransactionResponse = tokenClient.unpause(tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I update the treasury of token to operator")
    public void updateTokenTreasuryToOperator() {
        try {
            var accountId = accountClient.getAccount(AccountNameEnum.OPERATOR);
            networkTransactionResponse = tokenClient.updateTokenTreasury(tokenId, accountId);
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(ReceiptStatusException.class);
            ReceiptStatusException actualException = (ReceiptStatusException) exception;
            assertThat(actualException.receipt.status).isEqualTo(CURRENT_TREASURY_STILL_OWNS_NFTS);
        }
    }

    @Given("I update the treasury of token to {account}")
    public void updateTokenTreasury(AccountNameEnum accountNameEnum) {
        try {
            var accountId = accountClient.getAccount(accountNameEnum);
            networkTransactionResponse = tokenClient.updateTokenTreasury(tokenId, accountId);
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(ReceiptStatusException.class);
            ReceiptStatusException actualException = (ReceiptStatusException) exception;
            assertThat(actualException.receipt.status).isEqualTo(CURRENT_TREASURY_STILL_OWNS_NFTS);
        }
    }

    @Given("I burn {int} from the token")
    public void burnToken(int amount) {
        networkTransactionResponse = tokenClient.burnFungible(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I burn serial number {int} from token")
    public void burnNft(int serialNumberIndex) {
        networkTransactionResponse = tokenClient.burnNonFungible(
                tokenId, tokenSerialNumbers.get(tokenId).get(serialNumberIndex));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint {int} from the token")
    public void mintToken(int amount) {
        networkTransactionResponse = tokenClient.mint(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint a serial number from the token")
    public void mintNftToken() {
        networkTransactionResponse = tokenClient.mint(tokenId, nextBytes(4));
        assertNotNull(networkTransactionResponse.getTransactionId());
        TransactionReceipt receipt = networkTransactionResponse.getReceipt();
        assertNotNull(receipt);
        assertThat(receipt.serials.size()).isOne();
        long serialNumber = receipt.serials.get(0);
        assertThat(serialNumber).isPositive();
        tokenSerialNumbers.get(tokenId).add(serialNumber);
    }

    @Given("I wipe {int} from the token")
    public void wipeToken(int amount) {
        networkTransactionResponse = tokenClient.wipeFungible(tokenId, amount, getRecipientAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I wipe serial number {int} from token")
    public void wipeNft(int serialNumberIndex) {
        networkTransactionResponse = tokenClient.wipeNonFungible(
                tokenId, tokenSerialNumbers.get(tokenId).get(serialNumberIndex), getRecipientAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I dissociate the account from the token")
    public void dissociateNewAccountFromToken() {
        networkTransactionResponse = tokenClient.dissociate(getRecipientAccountId(), tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the token")
    public void deleteToken() {
        var operatorId = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.delete(operatorId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I update token with new custom fees schedule")
    public void updateTokenFeeSchedule(List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.updateTokenFeeSchedule(tokenId, admin, customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        this.customFees = customFees;
    }

    @Then("the mirror node Token Info REST API should return pause status {pauseStatus}")
    @RetryAsserts
    public void verifyTokenPauseStatus(PauseStatusEnum pauseStatus) {
        verifyTokenPauseStatus(tokenId, pauseStatus);
    }

    @Then("the mirror node REST API should return the transaction")
    @RetryAsserts
    public void verifyMirrorAPIResponses() {
        verifyTransactions();
    }

    @Then("the mirror node REST API should return the transaction for token serial number {int} transaction flow")
    @RetryAsserts
    public void verifyMirrorNftTransactionsAPIResponses(Integer serialNumberIndex) {
        Long serialNumber = tokenSerialNumbers.get(tokenId).get(getIndexOrDefault(serialNumberIndex));
        verifyTransactions();
        verifyNftTransactions(tokenId, serialNumber);
    }

    @Then("the mirror node REST API should return the transaction for token fund flow")
    @RetryAsserts
    public void verifyMirrorTokenFundFlow() {
        verifyMirrorTokenFundFlow(tokenId, Collections.emptyList());
    }

    @Then("the mirror node REST API should return the transaction for token fund flow with assessed custom fees")
    @RetryAsserts
    public void verifyMirrorTokenFundFlow(List<AssessedCustomFee> assessedCustomFees) {
        verifyMirrorTokenFundFlow(tokenId, assessedCustomFees);
    }

    private void verifyMirrorTokenFundFlow(TokenId tokenId, List<AssessedCustomFee> assessedCustomFees) {
        verifyTransactions(assessedCustomFees);
        verifyToken(tokenId);
        verifyTokenTransfers(tokenId);
    }

    @Then("the mirror node REST API should return the transaction for token serial number {int} full flow")
    @RetryAsserts
    public void verifyMirrorNftFundFlow(Integer serialNumberIndex) {
        Long serialNumber = tokenSerialNumbers.get(tokenId).get(getIndexOrDefault(serialNumberIndex));
        verifyTransactions();
        verifyToken(tokenId);
        verifyNft(tokenId, serialNumber);
        verifyNftTransfers(tokenId, serialNumber);
        verifyNftTransactions(tokenId, serialNumber);
    }

    @Then("the mirror node REST API should confirm token update")
    @RetryAsserts
    public void verifyMirrorTokenUpdateFlow() {
        verifyTokenUpdate(tokenId);
    }

    @Then("the mirror node REST API should return the transaction for transaction {string}")
    @RetryAsserts
    public void verifyMirrorRestTransactionIsPresent(int status, String transactionIdString) {
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionIdString);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionIdString);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }
    }

    @Then("the mirror node REST API should confirm token with custom fees schedule")
    @RetryAsserts
    public void verifyMirrorTokenWithCustomFeesSchedule() {
        var transaction = verifyTransactions();
        verifyTokenWithCustomFeesSchedule(tokenId, transaction.getConsensusTimestamp());
    }

    @Then("the mirror node REST API should return the token relationship for token")
    @RetryAsserts
    public void verifyMirrorTokenRelationshipTokenAPIResponses() {
        TokenRelationshipResponse mirrorTokenRelationship = callTokenRelationship(tokenId);
        // Asserting values
        assertTokenRelationship(mirrorTokenRelationship);
        TokenRelationship token = mirrorTokenRelationship.getTokens().get(0);
        assertThat(token.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(token.getFreezeStatus()).isEqualTo(FreezeStatusEnum.UNFROZEN);
        assertThat(token.getKycStatus()).isEqualTo(KycStatusEnum.REVOKED);
        assertThat(token.getDecimals()).isNotNull();
    }

    @Then("the mirror node REST API should return the token relationship for nft")
    @RetryAsserts
    public void verifyMirrorTokenRelationshipNftAPIResponses() {
        TokenRelationshipResponse mirrorTokenRelationship = callTokenRelationship(tokenId);
        // Asserting values
        assertTokenRelationship(mirrorTokenRelationship);
        TokenRelationship token = mirrorTokenRelationship.getTokens().get(0);
        assertThat(token.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(token.getFreezeStatus()).isEqualTo(FreezeStatusEnum.NOT_APPLICABLE);
        assertThat(token.getKycStatus()).isEqualTo(KycStatusEnum.NOT_APPLICABLE);
    }

    @Then("the mirror node REST API should confirm the approved transfer of {long} tokens")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenTransferResponse(long transferAmount) {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var expectedTokenTransfer = new TransactionTokenTransfersInner()
                .tokenId(tokenId.toString())
                .account(owner)
                .amount(-transferAmount)
                .isApproval(true);
        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).hasSize(1).first().satisfies(t -> assertThat(t.getTokenTransfers())
                .contains(expectedTokenTransfer));
    }

    public ExpandedAccountId getRecipientAccountId() {
        return accountClient.getAccount(AccountNameEnum.ALICE);
    }

    private void associateWithToken(ExpandedAccountId accountId, TokenId tokenId) {
        networkTransactionResponse = tokenClient.associate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setFreezeStatus(int freezeStatus, ExpandedAccountId accountId) {
        if (freezeStatus == TokenFreezeStatus.Frozen_VALUE) {
            networkTransactionResponse = tokenClient.freeze(tokenId, accountId.getAccountId());
        } else if (freezeStatus == TokenFreezeStatus.Unfrozen_VALUE) {
            networkTransactionResponse = tokenClient.unfreeze(tokenId, accountId.getAccountId());
        } else {
            log.warn("Freeze Status must be set to 1 (Frozen) or 2 (Unfrozen)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setKycStatus(int kycStatus, ExpandedAccountId accountId) {
        if (kycStatus == TokenKycStatus.Granted_VALUE) {
            networkTransactionResponse = tokenClient.grantKyc(tokenId, accountId.getAccountId());
        } else if (kycStatus == TokenKycStatus.Revoked_VALUE) {
            networkTransactionResponse = tokenClient.revokeKyc(tokenId, accountId.getAccountId());
        } else {
            log.warn("Kyc Status must be set to 1 (Granted) or 2 (Revoked)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void transferTokens(TokenId tokenId, int amount, AccountNameEnum sender, AccountNameEnum receiver) {
        transferTokens(tokenId, amount, sender, receiver, 0);
    }

    private void transferTokens(
            TokenId tokenId, int amount, AccountNameEnum senderName, AccountNameEnum recieverName, int fractionalFee) {
        var sender = senderName != null
                ? accountClient.getAccount(senderName)
                : tokenClient.getSdkClient().getExpandedOperatorAccountId();
        var receiver = accountClient.getAccount(recieverName).getAccountId();
        long startingBalance = getTokenBalance(receiver, tokenId);
        long expectedBalance = startingBalance + amount - fractionalFee;

        networkTransactionResponse = tokenClient.transferFungibleToken(tokenId, sender, receiver, null, amount);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertThat(getTokenBalance(receiver, tokenId)).isEqualTo(expectedBalance);
    }

    private long getTokenBalance(AccountId accountId, TokenId tokenId) {
        verifyTransactions(null);
        var tokenRelationships =
                mirrorClient.getTokenRelationships(accountId, tokenId).getTokens();
        assertThat(tokenRelationships).isNotNull().hasSize(1);
        return tokenRelationships.get(0).getBalance();
    }

    private void transferNfts(
            TokenId tokenId, long serialNumber, ExpandedAccountId sender, AccountNameEnum accountName) {
        var receiver = accountClient.getAccount(accountName).getAccountId();
        long startingBalance = getTokenBalance(receiver, tokenId);
        networkTransactionResponse =
                tokenClient.transferNonFungibleToken(tokenId, sender, receiver, List.of(serialNumber), null);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertThat(getTokenBalance(receiver, tokenId)).isEqualTo(startingBalance + 1);
    }

    private TransactionDetail verifyTransactions() {
        return verifyTransactions(Collections.emptyList());
    }

    private TransactionDetail verifyTransactions(List<AssessedCustomFee> assessedCustomFees) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");

        if (!CollectionUtils.isEmpty(assessedCustomFees)) {
            assertThat(mirrorTransaction.getAssessedCustomFees())
                    .containsExactlyInAnyOrderElementsOf(assessedCustomFees);
        }

        return mirrorTransaction;
    }

    private NftTransactionTransfer verifyNftTransactions(TokenId tokenId, Long serialNumber) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        NftTransactionHistory mirrorTransactionsResponse = mirrorClient.getNftTransactions(tokenId, serialNumber);

        List<NftTransactionTransfer> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        NftTransactionTransfer mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);

        return mirrorTransaction;
    }

    private TokenInfo verifyToken(TokenId tokenId) {
        TokenInfo mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());

        return mirrorToken;
    }

    private Nft verifyNft(TokenId tokenId, Long serialNumber) {
        Nft mirrorNft = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);

        assertNotNull(mirrorNft);
        assertThat(mirrorNft.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(mirrorNft.getSerialNumber()).isEqualTo(serialNumber);

        return mirrorNft;
    }

    private void verifyTokenTransfers(TokenId tokenId) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getName()).isEqualTo(CRYPTOTRANSFER);

        boolean tokenIdFound = false;

        String tokenIdString = tokenId.toString();
        for (TransactionTokenTransfersInner tokenTransfer : mirrorTransaction.getTokenTransfers()) {
            if (tokenTransfer.getTokenId().equalsIgnoreCase(tokenIdString)) {
                tokenIdFound = true;
                break;
            }
        }

        assertTrue(tokenIdFound);
    }

    private void verifyNftTransfers(TokenId tokenId, Long serialNumber) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());

        assertThat(mirrorTransaction.getNftTransfers())
                .filteredOn(transfer -> tokenId.toString().equals(transfer.getTokenId()))
                .map(TransactionNftTransfersInner::getSerialNumber)
                .containsExactly(serialNumber);
    }

    private void verifyTokenUpdate(TokenId tokenId) {
        TokenInfo mirrorToken = verifyToken(tokenId);

        assertThat(mirrorToken.getCreatedTimestamp()).isNotEqualTo(mirrorToken.getModifiedTimestamp());
    }

    private void verifyTokenPauseStatus(TokenId tokenId, PauseStatusEnum pauseStatus) {
        TokenInfo mirrorToken = verifyToken(tokenId);

        assertThat(mirrorToken.getPauseStatus()).isEqualTo(pauseStatus);
    }

    private void verifyTokenWithCustomFeesSchedule(TokenId tokenId, String createdTimestamp) {
        TokenInfo response = verifyToken(tokenId);

        CustomFees expected = new CustomFees()
                .createdTimestamp(createdTimestamp)
                .fixedFees(new ArrayList<>())
                .fractionalFees(new ArrayList<>());

        for (CustomFee customFee : customFees) {
            if (customFee instanceof CustomFixedFee sdkFixedFee) {
                FixedFee fixedFee = new FixedFee();

                fixedFee.allCollectorsAreExempt(false);
                fixedFee.amount(sdkFixedFee.getAmount());
                fixedFee.collectorAccountId(
                        sdkFixedFee.getFeeCollectorAccountId().toString());

                if (sdkFixedFee.getDenominatingTokenId() != null) {
                    fixedFee.denominatingTokenId(
                            sdkFixedFee.getDenominatingTokenId().toString());
                }

                expected.getFixedFees().add(fixedFee);
            } else {
                CustomFractionalFee sdkFractionalFee = (CustomFractionalFee) customFee;
                FractionalFee fractionalFee = new FractionalFee();

                FractionalFeeAmount fraction = new FractionalFeeAmount();
                fraction.numerator(sdkFractionalFee.getNumerator());
                fraction.denominator(sdkFractionalFee.getDenominator());
                fractionalFee.allCollectorsAreExempt(false);
                fractionalFee.amount(fraction);

                fractionalFee.collectorAccountId(
                        sdkFractionalFee.getFeeCollectorAccountId().toString());
                fractionalFee.denominatingTokenId(tokenId.toString());

                if (sdkFractionalFee.getMax() != 0) {
                    fractionalFee.maximum(sdkFractionalFee.getMax());
                }

                fractionalFee.minimum(sdkFractionalFee.getMin());

                expected.getFractionalFees().add(fractionalFee);
            }
        }

        CustomFees actual = response.getCustomFees();

        assertThat(actual)
                .usingRecursiveComparison(RecursiveComparisonConfiguration.builder()
                        .withIgnoreCollectionOrder(true)
                        .build())
                .isEqualTo(expected);
    }

    private void verifyMirrorAPIApprovedTokenAllowanceResponse(
            TokenId tokenId, ExpandedAccountId spenderAccountId, long approvedAmount, long transferAmount) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        var token = tokenId.toString();
        var spender = spenderAccountId.getAccountId().toString();
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var mirrorTokenAllowanceResponse = mirrorClient.getAccountTokenAllowanceBySpender(owner, token, spender);
        var remainingAmount = approvedAmount - transferAmount;

        // verify valid set of allowance
        assertThat(mirrorTokenAllowanceResponse.getAllowances())
                .isNotEmpty()
                .first()
                .isNotNull()
                .returns(remainingAmount, TokenAllowance::getAmount)
                .returns(approvedAmount, TokenAllowance::getAmountGranted)
                .returns(owner, TokenAllowance::getOwner)
                .returns(spender, TokenAllowance::getSpender)
                .returns(token, TokenAllowance::getTokenId)
                .extracting(TokenAllowance::getTimestamp)
                .isNotNull()
                .satisfies(t -> assertThat(t.getFrom()).isNotBlank())
                .satisfies(t -> assertThat(t.getTo()).isBlank());
    }

    private int getIndexOrDefault(Integer index) {
        return index != null ? index : 0;
    }

    private TokenRelationshipResponse callTokenRelationship(TokenId tokenId) {
        var accountId = getRecipientAccountId();
        return mirrorClient.getTokenRelationships(accountId.getAccountId(), tokenId);
    }

    private void assertTokenRelationship(TokenRelationshipResponse mirrorTokenRelationship) {
        assertNotNull(mirrorTokenRelationship);
        assertNotNull(mirrorTokenRelationship.getTokens());
        assertNotNull(mirrorTokenRelationship.getLinks());
        assertNotEquals(0, mirrorTokenRelationship.getTokens().size());
        assertThat(mirrorTokenRelationship.getLinks().getNext()).isNull();
    }
}
