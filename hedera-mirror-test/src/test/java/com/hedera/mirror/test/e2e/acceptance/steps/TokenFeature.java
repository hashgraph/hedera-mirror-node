/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorAssessedCustomFee;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorCustomFees;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorFixedFee;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorFraction;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorFractionalFee;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorFreezeStatus;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorKycStatus;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorNftTransaction;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorNftTransfer;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenAccount;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenAllowance;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenTransfer;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorNftResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorNftTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenRelationshipResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import io.cucumber.java.After;
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
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@CustomLog
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class TokenFeature extends AbstractFeature {
    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final int MAX_SUPPLY = 1;

    private final TokenClient tokenClient;
    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;

    // Legacy (index based) tracking
    private final List<ExpandedAccountId> recipients = new ArrayList<>();
    private final List<ExpandedAccountId> senders = new ArrayList<>();
    private final Map<TokenId, List<CustomFee>> tokenCustomFees = new HashMap<>();
    private final Map<TokenId, List<Long>> tokenSerialNumbers = new HashMap<>();
    private final List<TokenId> tokenIds = new ArrayList<>();

    @Given("I associate account {string} with token {string}")
    public void associateToken(String accountName, String tokenName) {
        var accountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        associateWithToken(accountId, tokenId);
    }

    @Given("I approve {string} to transfer up to {long} of token {string}")
    public void setFungibleTokenAllowance(String accountName, long amount, String tokenName) {
        var spenderAccountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(accountName))
                .getAccountId();
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        networkTransactionResponse = accountClient.approveToken(tokenId, spenderAccountId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should confirm the approved allowance {long} of {string} for {string}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenAllowanceResponse(
            long approvedAmount, String tokenName, String accountName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var spenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        verifyMirrorAPIApprovedTokenAllowanceResponse(tokenId, spenderAccountId, approvedAmount, 0);
    }

    @Then("the mirror node REST API should confirm the debit of {long} from {string} allowance of {long} for {string}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedDebitedTokenAllowanceResponse(
            long debitAmount, String tokenName, long approvedAmount, String accountName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var spenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        verifyMirrorAPIApprovedTokenAllowanceResponse(tokenId, spenderAccountId, approvedAmount, debitAmount);
    }

    @Then("I transfer {long} of token {string} to {string}")
    public void transferTokensToRecipient(long amount, String tokenName, String accountName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var recipientAccountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(accountName))
                .getAccountId();
        var ownerAccountId = tokenClient.getSdkClient().getExpandedOperatorAccountId();

        networkTransactionResponse = tokenClient.transferFungibleToken(
                tokenId, ownerAccountId.getAccountId(), ownerAccountId, recipientAccountId, amount, false);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("{string} transfers {long} of token {string} to {string}")
    public void transferFromAllowance(
            String spenderAccountName, long amount, String tokenName, String recipientAccountName) {

        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var spenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(spenderAccountName));
        var recipientAccountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(recipientAccountName))
                .getAccountId();
        var ownerAccountId = accountClient.getClient().getOperatorAccountId();

        networkTransactionResponse = tokenClient.transferFungibleToken(
                tokenId, ownerAccountId, spenderAccountId, recipientAccountId, amount, true);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should confirm the transfer of {long} {string}")
    @RetryAsserts
    public void verifyMirrorAPITokenTransferResponse(long transferAmount, String tokenName) {
        verifyMirrorAPIApprovedTokenTransferResponse(transferAmount, tokenName, false);
    }

    @Then("the mirror node REST API should confirm the approved transfer of {long} {string}")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenTransferResponse(long transferAmount, String tokenName) {
        verifyMirrorAPIApprovedTokenTransferResponse(transferAmount, tokenName, true);
    }

    private void verifyMirrorAPIApprovedTokenTransferResponse(
            long transferAmount, String tokenName, boolean isApproval) {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        var tokenId = tokenClient
                .getToken(TokenClient.TokenNameEnum.valueOf(tokenName))
                .toString();
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).hasSize(1).first().satisfies(t -> assertThat(t.getTokenTransfers())
                .contains(MirrorTokenTransfer.builder()
                        .tokenId(tokenId)
                        .account(owner)
                        .amount(-transferAmount)
                        .isApproval(isApproval)
                        .build()));
    }

    @Given("I delete the allowance on token {string} for {string}")
    public void setFungibleTokenAllowance(String tokenName, String spenderAccountName) {
        var tokenId = tokenClient.getToken(TokenClient.TokenNameEnum.valueOf(tokenName));
        var spenderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(spenderAccountName));
        networkTransactionResponse = accountClient.approveToken(tokenId, spenderAccountId.getAccountId(), 0L);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I successfully create a new token")
    public void createNewToken() {
        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully create a new token with custom fees schedule")
    public void createNewToken(List<CustomFee> customFees) {
        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                customFees);
    }

    @Given("I successfully create a new token with freeze status {int} and kyc status {int}")
    public void createNewToken(int freezeStatus, int kycStatus) {
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);
    }

    @Given("I successfully create a new nft with supplyType {string}")
    public void createNewNft(String tokenSupplyType) {
        createNewNft(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                TokenSupplyType.valueOf(tokenSupplyType));
    }

    @Given("^I associate a(?:n)? (?:existing|new) sender account(?: (.*))? with token(?: (.*))?$")
    public void associateSenderWithToken(Integer senderIndex, Integer tokenIndex) {
        ExpandedAccountId sender;
        if (senderIndex == null) {
            sender = accountClient.createNewAccount(10_000_000);
            senders.add(sender);
        } else {
            sender = senders.get(senderIndex);
        }

        associateWithToken(sender, tokenIds.get(getIndexOrDefault(tokenIndex)));
    }

    @Given("^I associate a(?:n)? (?:existing|new) recipient account(?: (.*))? with token(?: (.*))?$")
    public void associateRecipientWithToken(Integer recipientIndex, Integer tokenIndex) {
        ExpandedAccountId recipient;
        if (recipientIndex == null) {
            recipient = accountClient.createNewAccount(10_000_000);
            recipients.add(recipient);
        } else {
            recipient = recipients.get(recipientIndex);
        }

        associateWithToken(recipient, tokenIds.get(getIndexOrDefault(tokenIndex)));
    }

    @When("^I set new account (?:(.*) )?freeze status to (.*)$")
    public void setFreezeStatus(Integer recipientIndex, int freezeStatus) {
        setFreezeStatus(freezeStatus, recipients.get(getIndexOrDefault(recipientIndex)));
    }

    @When("^I set new account (?:(.*) )?kyc status to (.*)$")
    public void setKycStatus(Integer recipientIndex, int kycStatus) {
        setKycStatus(kycStatus, recipients.get(getIndexOrDefault(recipientIndex)));
    }

    @Then("^I transfer (.*) tokens (?:(.*) )?to recipient(?: (.*))?$")
    public void transferTokensToRecipient(int amount, Integer tokenIndex, Integer recipientIndex) {
        ExpandedAccountId payer = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        transferTokens(
                tokenIds.get(getIndexOrDefault(tokenIndex)),
                amount,
                payer,
                recipients.get(getIndexOrDefault(recipientIndex)).getAccountId());
    }

    @Then("^I transfer (.*) tokens (?:(.*) )?to sender(?: (.*))?$")
    public void transferTokensToSender(int amount, Integer tokenIndex, Integer senderIndex) {
        ExpandedAccountId payer = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        transferTokens(
                tokenIds.get(getIndexOrDefault(tokenIndex)),
                amount,
                payer,
                senders.get(getIndexOrDefault(senderIndex)).getAccountId());
    }

    @Then("^I transfer serial number (?:(.*) )?of token (?:(.*) )?to recipient(?: (.*))?$")
    public void transferNftsToRecipient(Integer serialNumberIndex, Integer tokenIndex, Integer recipientIndex) {
        ExpandedAccountId payer = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        TokenId tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
        transferNfts(
                tokenId,
                tokenSerialNumbers.get(tokenId).get(getIndexOrDefault(serialNumberIndex)),
                payer,
                recipients.get(getIndexOrDefault(recipientIndex)).getAccountId());
    }

    @Then("Sender {int} transfers {int} tokens {int} to recipient {int} with fractional fee {int}")
    public void transferTokensFromSenderToRecipientWithFee(
            int senderIndex, int amount, int tokenIndex, int recipientIndex, int fractionalFee) {
        transferTokens(
                tokenIds.get(tokenIndex),
                amount,
                senders.get(senderIndex),
                recipients.get(recipientIndex).getAccountId(),
                fractionalFee);
    }

    @Given("^I update the token(?: (.*))?$")
    public void updateToken(Integer index) {
        networkTransactionResponse = tokenClient.updateToken(
                tokenIds.get(getIndexOrDefault(index)),
                tokenClient.getSdkClient().getExpandedOperatorAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("^I pause the token(?: (.*))?$")
    public void pauseToken(Integer index) {
        networkTransactionResponse = tokenClient.pause(tokenIds.get(getIndexOrDefault(index)));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("^I unpause the token(?: (.*))?$")
    public void unpauseToken(Integer index) {
        networkTransactionResponse = tokenClient.unpause(tokenIds.get(getIndexOrDefault(index)));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("^I update the treasury of token(?: (.*))? to recipient(?: (.*))?$")
    public void updateTokenTreasury(Integer tokenIndex, Integer recipientIndex) {
        try {
            networkTransactionResponse = tokenClient.updateTokenTreasury(
                    tokenIds.get(getIndexOrDefault(tokenIndex)), recipients.get(getIndexOrDefault(recipientIndex)));
        } catch (Exception exception) {
            assertThat(exception).isInstanceOf(ReceiptStatusException.class);
            ReceiptStatusException actualException = (ReceiptStatusException) exception;
            assertThat(actualException.receipt.status).isEqualTo(CURRENT_TREASURY_STILL_OWNS_NFTS);
        }
    }

    @Given("I burn {int} from the token")
    public void burnToken(int amount) {
        networkTransactionResponse = tokenClient.burnFungible(tokenIds.get(0), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I burn serial number {int} from token {int}")
    public void burnNft(int serialNumberIndex, int tokenIndex) {
        TokenId tokenId = tokenIds.get(tokenIndex);
        networkTransactionResponse = tokenClient.burnNonFungible(
                tokenId, tokenSerialNumbers.get(tokenId).get(serialNumberIndex));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint {int} from the token")
    public void mintToken(int amount) {
        networkTransactionResponse = tokenClient.mint(tokenIds.get(0), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint a serial number from the token")
    public void mintNftToken() {
        TokenId tokenId = tokenIds.get(0);
        networkTransactionResponse = tokenClient.mint(tokenId, RandomUtils.nextBytes(4));
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
        networkTransactionResponse = tokenClient.wipeFungible(tokenIds.get(0), amount, recipients.get(0));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I wipe serial number {int} from token {int}")
    public void wipeNft(int serialNumberIndex, int tokenIndex) {
        TokenId tokenId = tokenIds.get(tokenIndex);
        networkTransactionResponse = tokenClient.wipeNonFungible(
                tokenId, tokenSerialNumbers.get(tokenId).get(serialNumberIndex), recipients.get(0));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I dissociate the account from the token")
    public void dissociateNewAccountFromToken() {
        networkTransactionResponse = tokenClient.dissociate(recipients.get(0), tokenIds.get(0));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the token")
    public void deleteToken() {
        networkTransactionResponse =
                tokenClient.delete(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenIds.get(0));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I update token {int} with new custom fees schedule")
    public void updateTokenFeeSchedule(int tokenIndex, List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        TokenId tokenId = tokenIds.get(tokenIndex);
        networkTransactionResponse = tokenClient.updateTokenFeeSchedule(tokenId, admin, customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenCustomFees.put(tokenId, customFees);
    }

    @Then("the mirror node Token Info REST API should return pause status {string}")
    @RetryAsserts
    public void verifyTokenPauseStatus(String status) {
        verifyTokenPauseStatus(tokenIds.get(0), status);
    }

    @Then("the mirror node REST API should return the transaction")
    @RetryAsserts
    public void verifyMirrorAPIResponses() {
        verifyTransactions();
    }

    @Then("^the mirror node REST API should return the transaction for token (:?(.*) )?serial number "
            + "(:?(.*) )?transaction flow$")
    @RetryAsserts
    public void verifyMirrorNftTransactionsAPIResponses(Integer tokenIndex, Integer serialNumberIndex) {
        TokenId tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
        Long serialNumber = tokenSerialNumbers.get(tokenId).get(getIndexOrDefault(serialNumberIndex));
        verifyTransactions();
        verifyNftTransactions(tokenId, serialNumber);
    }

    @Then("^the mirror node REST API should return the transaction for token (:?(.*) )?fund flow$")
    @RetryAsserts
    public void verifyMirrorTokenFundFlow(Integer tokenIndex) {
        verifyMirrorTokenFundFlow(tokenIndex, Collections.emptyList());
    }

    @Then("^the mirror node REST API should return the transaction for token (:?(.*) )?fund flow with assessed custom"
            + " "
            + "fees$")
    @RetryAsserts
    public void verifyMirrorTokenFundFlow(Integer tokenIndex, List<MirrorAssessedCustomFee> assessedCustomFees) {
        verifyMirrorTokenFundFlow(tokenIds.get(getIndexOrDefault(tokenIndex)), assessedCustomFees);
    }

    private void verifyMirrorTokenFundFlow(TokenId tokenId, List<MirrorAssessedCustomFee> assessedCustomFees) {
        verifyTransactions(assessedCustomFees);
        verifyToken(tokenId);
        verifyTokenTransfers(tokenId);
    }

    @Then("^the mirror node REST API should return the transaction for token (:?(.*) )?serial number (:?(.*) )?full "
            + "flow$")
    @RetryAsserts
    public void verifyMirrorNftFundFlow(Integer tokenIndex, Integer serialNumberIndex) {
        TokenId tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
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
        verifyTokenUpdate(tokenIds.get(0));
    }

    @Then("the mirror node REST API should return the transaction for transaction {string}")
    @RetryAsserts
    public void verifyMirrorRestTransactionIsPresent(int status, String transactionIdString) {
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionIdString);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionIdString);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }
    }

    @Then("the mirror node REST API should confirm token {int} with custom fees schedule")
    @RetryAsserts
    public void verifyMirrorTokenWithCustomFeesSchedule(Integer tokenIndex) {
        MirrorTransaction transaction = verifyTransactions();

        TokenId tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
        verifyTokenWithCustomFeesSchedule(tokenId, transaction.getConsensusTimestamp());
    }

    @Then("the mirror node REST API should return the token relationship for token")
    @RetryAsserts
    public void verifyMirrorTokenRelationshipTokenAPIResponses() {
        TokenId tokenId = tokenIds.get(0);
        MirrorTokenRelationshipResponse mirrorTokenRelationship = callTokenRelationship(tokenId);
        // Asserting values
        assertTokenRelationship(mirrorTokenRelationship);
        MirrorTokenAccount token = mirrorTokenRelationship.getTokens().get(0);
        assertThat(token.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(token.getFreezeStatus()).isEqualTo(MirrorFreezeStatus.UNFROZEN);
        assertThat(token.getKycStatus()).isEqualTo(MirrorKycStatus.REVOKED);
    }

    @Then("the mirror node REST API should return the token relationship for nft")
    @RetryAsserts
    public void verifyMirrorTokenRelationshipNftAPIResponses() {
        TokenId tokenId = tokenIds.get(0);
        MirrorTokenRelationshipResponse mirrorTokenRelationship = callTokenRelationship(tokenId);
        // Asserting values
        assertTokenRelationship(mirrorTokenRelationship);
        MirrorTokenAccount token = mirrorTokenRelationship.getTokens().get(0);
        assertThat(token.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(token.getFreezeStatus()).isEqualTo(MirrorFreezeStatus.NOT_APPLICABLE);
        assertThat(token.getKycStatus()).isEqualTo(MirrorKycStatus.NOT_APPLICABLE);
    }

    @Given("^I approve sender(?: (.*))? to transfer up to (.*) tokens (?:(.*))?$")
    public void setFungibleTokenAllowance(Integer senderIndex, long amount, Integer tokenIndex) {
        var tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
        var spenderAccountId = senders.get(getIndexOrDefault(senderIndex));
        networkTransactionResponse = accountClient.approveToken(tokenId, spenderAccountId.getAccountId(), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the allowance on token {int} for sender {int}")
    public void setFungibleTokenAllowance(Integer tokenIndex, Integer senderIndex) {
        var tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
        var spenderAccountId = senders.get(getIndexOrDefault(senderIndex));
        networkTransactionResponse = accountClient.approveToken(tokenId, spenderAccountId.getAccountId(), 0L);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then(
            "the mirror node REST API should confirm the approved token (?:(.*) )?allowance of (.*) for sender(?: (.*))?$")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenAllowanceResponse(
            Integer tokenIndex, long approvedAmount, Integer senderIndex) {
        verifyMirrorAPIApprovedDebitedTokenAllowanceResponse(0L, tokenIndex, approvedAmount, senderIndex);
    }

    @Then("the mirror node REST API should confirm the token allowance deletion")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenAllowanceDeletionResponse() {
        verifyMirrorAPIApprovedDebitedTokenAllowanceResponse(0L, 0, 0L, 0);
    }

    @Then(
            "the mirror node REST API should confirm the debit of (.*) from approved token (?:(.*) )?allowance of (.*) for sender(?: (.*))?$")
    @RetryAsserts
    public void verifyMirrorAPIApprovedDebitedTokenAllowanceResponse(
            long debitAmount, Integer tokenIndex, long approvedAmount, Integer senderIndex) {
        var tokenId = tokenIds.get(getIndexOrDefault(tokenIndex));
        var spenderAccountId = senders.get(getIndexOrDefault(senderIndex));
        verifyMirrorAPIApprovedTokenAllowanceResponse(tokenId, spenderAccountId, approvedAmount, debitAmount);
    }

    @Given("Sender {int} transfers {int} tokens {int} from the approved allowance to recipient {int}")
    public void transferFromAllowance(int senderIndex, int amount, int tokenIndex, int recipientIndex) {

        var tokenId = tokenIds.get(tokenIndex);
        var spenderAccountId = senders.get(senderIndex);
        var recipientAccountId = recipients.get(recipientIndex).getAccountId();
        var ownerAccountId = accountClient.getClient().getOperatorAccountId();

        networkTransactionResponse = tokenClient.transferFungibleToken(
                tokenId, ownerAccountId, spenderAccountId, recipientAccountId, amount, true);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should confirm the approved transfer of {long} tokens")
    @RetryAsserts
    public void verifyMirrorAPIApprovedTokenTransferResponse(long transferAmount) {
        var transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        var mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        var tokenId = tokenIds.get(0).toString();
        var owner = accountClient.getClient().getOperatorAccountId().toString();
        var transactions = mirrorTransactionsResponse.getTransactions();
        assertThat(transactions).hasSize(1).first().satisfies(t -> assertThat(t.getTokenTransfers())
                .contains(MirrorTokenTransfer.builder()
                        .tokenId(tokenId)
                        .account(owner)
                        .amount(-transferAmount)
                        .isApproval(true)
                        .build()));
    }

    @After
    public void cleanup() {
        // dissociate all applicable accounts from token to reduce likelihood of max token association error
        for (TokenId tokenId : tokenIds) {
            // a nonzero balance will result in a TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES error
            // not possible to wipe a treasury account as it results in CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT error
            // as a result to dissociate first delete token
            ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
            try {
                tokenClient.delete(admin, tokenId);

                dissociateAccounts(tokenId, List.of(admin));
                dissociateAccounts(tokenId, recipients);
                dissociateAccounts(tokenId, senders);
            } catch (Exception ex) {
                log.warn("Error cleaning up token {} and associations error: {}", tokenId, ex);
            }
        }

        recipients.clear();
        senders.clear();
        tokenCustomFees.clear();
        tokenIds.clear();
        tokenSerialNumbers.clear();
    }

    public AccountId getRecipientAccountId(int index) {
        return recipients.get(index).getAccountId();
    }

    public TokenId getTokenId(int index) {
        return tokenIds.get(index);
    }

    private void dissociateAccounts(TokenId tokenId, List<ExpandedAccountId> accountIds) {
        for (ExpandedAccountId accountId : accountIds) {
            try {
                tokenClient.dissociate(accountId, tokenId);
            } catch (Exception ex) {
                log.warn("Error dissociating account {} from token {}, error: {}", accountId, tokenId, ex);
            }
        }
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
        tokenCustomFees.put(tokenId, customFees);

        return tokenId;
    }

    private void createNewToken(String symbol, int freezeStatus, int kycStatus) {
        createNewToken(
                symbol,
                freezeStatus,
                kycStatus,
                TokenType.FUNGIBLE_COMMON,
                TokenSupplyType.INFINITE,
                Collections.emptyList());
    }

    private void createNewNft(String symbol, int freezeStatus, int kycStatus, TokenSupplyType tokenSupplyType) {
        TokenId tokenId = createNewToken(
                symbol,
                freezeStatus,
                kycStatus,
                TokenType.NON_FUNGIBLE_UNIQUE,
                tokenSupplyType,
                Collections.emptyList());
        tokenSerialNumbers.put(tokenId, new ArrayList<>());
    }

    private void associateWithToken(ExpandedAccountId accountId, TokenId tokenId) {
        networkTransactionResponse = tokenClient.associate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setFreezeStatus(int freezeStatus, ExpandedAccountId accountId) {
        if (freezeStatus == TokenFreezeStatus.Frozen_VALUE) {
            networkTransactionResponse = tokenClient.freeze(tokenIds.get(0), accountId.getAccountId());
        } else if (freezeStatus == TokenFreezeStatus.Unfrozen_VALUE) {
            networkTransactionResponse = tokenClient.unfreeze(tokenIds.get(0), accountId.getAccountId());
        } else {
            log.warn("Freeze Status must be set to 1 (Frozen) or 2 (Unfrozen)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setKycStatus(int kycStatus, ExpandedAccountId accountId) {
        if (kycStatus == TokenKycStatus.Granted_VALUE) {
            networkTransactionResponse = tokenClient.grantKyc(tokenIds.get(0), accountId.getAccountId());
        } else if (kycStatus == TokenKycStatus.Revoked_VALUE) {
            networkTransactionResponse = tokenClient.revokeKyc(tokenIds.get(0), accountId.getAccountId());
        } else {
            log.warn("Kyc Status must be set to 1 (Granted) or 2 (Revoked)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void transferTokens(TokenId tokenId, int amount, ExpandedAccountId sender, AccountId receiver) {
        transferTokens(tokenId, amount, sender, receiver, 0);
    }

    private void transferTokens(
            TokenId tokenId, int amount, ExpandedAccountId sender, AccountId receiver, int fractionalFee) {
        long startingBalance = getTokenBalance(receiver, tokenId);
        long expectedBalance = startingBalance + amount - fractionalFee;

        networkTransactionResponse = tokenClient.transferFungibleToken(tokenId, sender, receiver, amount);

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

    private void transferNfts(TokenId tokenId, long serialNumber, ExpandedAccountId sender, AccountId receiver) {
        long startingBalance = getTokenBalance(receiver, tokenId);
        networkTransactionResponse =
                tokenClient.transferNonFungibleToken(tokenId, sender, receiver, List.of(serialNumber));

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertThat(getTokenBalance(receiver, tokenId)).isEqualTo(startingBalance + 1);
    }

    private MirrorTransaction verifyTransactions() {
        return verifyTransactions(Collections.emptyList());
    }

    private MirrorTransaction verifyTransactions(List<MirrorAssessedCustomFee> assessedCustomFees) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");

        if (assessedCustomFees != null) {
            assertThat(mirrorTransaction.getAssessedCustomFees())
                    .containsExactlyInAnyOrderElementsOf(assessedCustomFees);
        }

        return mirrorTransaction;
    }

    private MirrorNftTransaction verifyNftTransactions(TokenId tokenId, Long serialNumber) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorNftTransactionsResponse mirrorTransactionsResponse =
                mirrorClient.getNftTransactions(tokenId, serialNumber);

        List<MirrorNftTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorNftTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);

        return mirrorTransaction;
    }

    private MirrorTokenResponse verifyToken(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());

        return mirrorToken;
    }

    private MirrorNftResponse verifyNft(TokenId tokenId, Long serialNumber) {
        MirrorNftResponse mirrorNft = mirrorClient.getNftInfo(tokenId.toString(), serialNumber);

        assertNotNull(mirrorNft);
        assertThat(mirrorNft.getTokenId()).isEqualTo(tokenId.toString());
        assertThat(mirrorNft.getSerialNumber()).isEqualTo(serialNumber);

        return mirrorNft;
    }

    private void verifyTokenTransfers(TokenId tokenId) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getName()).isEqualTo("CRYPTOTRANSFER");

        boolean tokenIdFound = false;

        String tokenIdString = tokenId.toString();
        for (MirrorTokenTransfer tokenTransfer : mirrorTransaction.getTokenTransfers()) {
            if (tokenTransfer.getTokenId().equalsIgnoreCase(tokenIdString)) {
                tokenIdFound = true;
                break;
            }
        }

        assertTrue(tokenIdFound);
    }

    private void verifyNftTransfers(TokenId tokenId, Long serialNumber) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());

        assertThat(mirrorTransaction.getNftTransfers())
                .filteredOn(transfer -> tokenId.toString().equals(transfer.getTokenId()))
                .map(MirrorNftTransfer::getSerialNumber)
                .containsExactly(serialNumber);
    }

    private void verifyTokenUpdate(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = verifyToken(tokenId);

        assertThat(mirrorToken.getCreatedTimestamp()).isNotEqualTo(mirrorToken.getModifiedTimestamp());
    }

    private void verifyTokenPauseStatus(TokenId tokenId, String pauseStatus) {
        MirrorTokenResponse mirrorToken = verifyToken(tokenId);

        assertThat(mirrorToken.getPauseStatus()).isEqualTo(pauseStatus);
    }

    private void verifyTokenWithCustomFeesSchedule(TokenId tokenId, String createdTimestamp) {
        MirrorTokenResponse response = verifyToken(tokenId);

        MirrorCustomFees expected = new MirrorCustomFees();
        expected.setCreatedTimestamp(createdTimestamp);
        for (CustomFee customFee : tokenCustomFees.get(tokenId)) {
            if (customFee instanceof CustomFixedFee sdkFixedFee) {
                MirrorFixedFee fixedFee = new MirrorFixedFee();

                fixedFee.setAllCollectorsAreExempt(false);
                fixedFee.setAmount(sdkFixedFee.getAmount());
                fixedFee.setCollectorAccountId(
                        sdkFixedFee.getFeeCollectorAccountId().toString());

                if (sdkFixedFee.getDenominatingTokenId() != null) {
                    fixedFee.setDenominatingTokenId(
                            sdkFixedFee.getDenominatingTokenId().toString());
                }

                expected.getFixedFees().add(fixedFee);
            } else {
                CustomFractionalFee sdkFractionalFee = (CustomFractionalFee) customFee;
                MirrorFractionalFee fractionalFee = new MirrorFractionalFee();

                MirrorFraction fraction = new MirrorFraction();
                fraction.setNumerator(sdkFractionalFee.getNumerator());
                fraction.setDenominator(sdkFractionalFee.getDenominator());
                fractionalFee.setAllCollectorsAreExempt(false);
                fractionalFee.setAmount(fraction);

                fractionalFee.setCollectorAccountId(
                        sdkFractionalFee.getFeeCollectorAccountId().toString());
                fractionalFee.setDenominatingTokenId(tokenId.toString());

                if (sdkFractionalFee.getMax() != 0) {
                    fractionalFee.setMaximum(sdkFractionalFee.getMax());
                }

                fractionalFee.setMinimum(sdkFractionalFee.getMin());

                expected.getFractionalFees().add(fractionalFee);
            }
        }

        MirrorCustomFees actual = response.getCustomFees();

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
                .returns(remainingAmount, MirrorTokenAllowance::getAmount)
                .returns(approvedAmount, MirrorTokenAllowance::getAmountGranted)
                .returns(owner, MirrorTokenAllowance::getOwner)
                .returns(spender, MirrorTokenAllowance::getSpender)
                .returns(token, MirrorTokenAllowance::getTokenId)
                .extracting(MirrorTokenAllowance::getTimestamp)
                .isNotNull()
                .satisfies(t -> assertThat(t.getFrom()).isNotBlank())
                .satisfies(t -> assertThat(t.getTo()).isBlank());
    }

    private int getIndexOrDefault(Integer index) {
        return index != null ? index : 0;
    }

    private MirrorTokenRelationshipResponse callTokenRelationship(TokenId tokenId) {
        AccountId accountId = getRecipientAccountId(0);
        return mirrorClient.getTokenRelationships(accountId, tokenId);
    }

    private void assertTokenRelationship(MirrorTokenRelationshipResponse mirrorTokenRelationship) {
        assertNotNull(mirrorTokenRelationship);
        assertNotNull(mirrorTokenRelationship.getTokens());
        assertNotNull(mirrorTokenRelationship.getLinks());
        assertNotEquals(0, mirrorTokenRelationship.getTokens().size());
        assertThat(mirrorTokenRelationship.getLinks().getNext()).isNull();
    }
}
