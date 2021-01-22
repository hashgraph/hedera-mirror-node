package com.hedera.mirror.test.e2e.acceptance.steps;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.proto.TokenFreezeStatus;
import com.hedera.hashgraph.proto.TokenKycStatus;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorCryptoBalance;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenAccountBalance;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenTransfer;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorBalancesResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Cucumber
public class TokenFeature {
    private final static int INITIAL_SUPPLY = 1_000_000;

    @Autowired
    private AcceptanceTestProperties acceptanceProps;
    @Autowired
    private TokenClient tokenClient;
    @Autowired
    private AccountClient accountClient;
    @Autowired
    private MirrorNodeClient mirrorClient;
    private Instant testInstantReference;
    private Ed25519PrivateKey tokenKey;
    private String symbol;
    private TokenId tokenId;
    private ExpandedAccountId treasuryAccount;
    private ExpandedAccountId sender;
    private ExpandedAccountId recipient;
    private NetworkTransactionResponse networkTransactionResponse;
    private List<TransactionId> transactionIdList;

    @When("I create a new treasury account")
    public void createNewAccount() throws HederaStatusException {
        if (treasuryAccount == null) {
            treasuryAccount = accountClient.createNewAccount(1_000_000_000);
            log.debug("Treasury Account: {} will be used for current test session", treasuryAccount);
        }
    }

    @Given("I successfully create a new token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewToken() throws HederaStatusException {
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully create a new token {string}")
    public void createNewToken(String symbol) throws HederaStatusException {
        createNewToken(symbol, TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully onboard a new token account with freeze status {int} and kyc status {int}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewToken(int freezeStatus, int kycStatus) throws HederaStatusException {
        testInstantReference = Instant.now();

        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);
    }

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewToken(String symbol, int freezeStatus, int kycStatus) throws HederaStatusException {
        testInstantReference = Instant.now();

        tokenKey = Ed25519PrivateKey.generate();
        Ed25519PublicKey tokenPublicKey = tokenKey.publicKey;
        log.debug("Token creation PrivateKey : {}, PublicKey : {}", tokenKey, tokenPublicKey);

        networkTransactionResponse = tokenClient.createToken(
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                symbol,
                freezeStatus,
                kycStatus,
                treasuryAccount,
                INITIAL_SUPPLY);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenId = networkTransactionResponse.getReceipt().getTokenId();
        assertNotNull(tokenId);
    }

    @Given("I successfully onboard a new token account")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void onboardNewTokenAccount() throws HederaStatusException {
        onboardNewTokenAccount(TokenFreezeStatus.FreezeNotApplicable_VALUE, TokenKycStatus.KycNotApplicable_VALUE);
    }

    public void onboardNewTokenAccount(int freezeStatus, int kycStatus) throws HederaStatusException {
        // create token, associate payer and transfer tokens to payer
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);

        associateWithToken();

        fundPayerAccountWithTokens(INITIAL_SUPPLY / 2);
    }

    @Given("I provide a token symbol {string}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void designateToken(String symbol) {
        testInstantReference = Instant.now();

        this.symbol = symbol;
    }

    @Given("I associate with token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void associateWithToken() throws HederaStatusException {
        // associate payer
        sender = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        associateWithToken(sender);
    }

    @Given("I associate a new sender account with token")
    public void associateSenderWithToken() throws HederaStatusException {

        sender = accountClient.createNewAccount(10_000_000);
        associateWithToken(sender);
    }

    @Given("I associate a new recipient account with token")
    public void associateRecipientWithToken() throws HederaStatusException {

        recipient = accountClient.createNewAccount(10_000_000);
        associateWithToken(recipient);
    }

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void associateWithToken(ExpandedAccountId accountId) throws HederaStatusException {
        networkTransactionResponse = tokenClient.asssociate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I set new account freeze status to {int}")
    public void setFreezeStatus(int freezeStatus) throws HederaStatusException {
        setFreezeStatus(freezeStatus, recipient);
    }

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void setFreezeStatus(int freezeStatus, ExpandedAccountId accountId) throws HederaStatusException {
        if (freezeStatus == TokenFreezeStatus.Frozen_VALUE) {
            networkTransactionResponse = tokenClient.freeze(tokenId, accountId.getAccountId(), tokenKey);
        } else if (freezeStatus == TokenFreezeStatus.Unfrozen_VALUE) {
            networkTransactionResponse = tokenClient.unfreeze(tokenId, accountId.getAccountId(), tokenKey);
        } else {
            log.warn("Freeze Status must be set to 1 (Frozen) or 2 (Unfrozen)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I set new account kyc status to {int}")
    public void setKycStatus(int kycStatus) throws HederaStatusException {
        setKycStatus(kycStatus, recipient);
    }

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void setKycStatus(int kycStatus, ExpandedAccountId accountId) throws HederaStatusException {
        if (kycStatus == TokenKycStatus.Granted_VALUE) {
            networkTransactionResponse = tokenClient.grantKyc(tokenId, accountId.getAccountId(), tokenKey);
        } else if (kycStatus == TokenKycStatus.Revoked_VALUE) {
            networkTransactionResponse = tokenClient.revokeKyc(tokenId, accountId.getAccountId(), tokenKey);
        } else {
            log.warn("Kyc Status must be set to 1 (Granted) or 2 (Revoked)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("I fund {int} tokens to payer")
    public void fundPayerAccountWithTokens(int amount) throws HederaStatusException {
        transferTokens(tokenId, amount, treasuryAccount, tokenClient.getSdkClient().getOperatorId());
    }

    @Then("I transfer {int} tokens to recipient")
    public void transferTokensToRecipient(int amount) throws HederaStatusException {
        transferTokens(tokenId, amount, sender, recipient
                .getAccountId());
    }

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void transferTokens(TokenId tokenId, int amount, ExpandedAccountId sender, AccountId receiver) throws HederaStatusException {
        networkTransactionResponse = tokenClient.transferToken(tokenId, sender, receiver, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I update the token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void updateToken() throws HederaStatusException {

        networkTransactionResponse = tokenClient
                .updateToken(tokenId, tokenClient.getSdkClient().getExpandedOperatorAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I burn {int} from the token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void burnToken(int amount) throws HederaStatusException {

        networkTransactionResponse = tokenClient.burn(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint {int} from the token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void mintToken(int amount) throws HederaStatusException {

        networkTransactionResponse = tokenClient.mint(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I wipe {int} from the token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void wipeToken(int amount) throws HederaStatusException {

        networkTransactionResponse = tokenClient.wipe(tokenId, amount, recipient);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I dissociate the account from the token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void dissociateNewAccountFromToken() throws HederaStatusException {
        networkTransactionResponse = tokenClient.disssociate(recipient, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void deleteToken() throws HederaStatusException {

        networkTransactionResponse = tokenClient
                .delete(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should return status {int}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPIResponses(int status) {
        verifyTransactions(status);
    }

    @Then("the mirror node REST API should return status {int} for token fund flow")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenFundFlow(int status) {
        verifyBalances();
        verifyTransactions(status);
        verifyToken();
        verifyTokenTransfers();
    }

    @Then("the mirror node REST API should confirm token update")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenUpdateFlow() {
        verifyTokenUpdate();
    }

    @Then("the mirror node REST API should return status {int} for transaction {string}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
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

    private void verifyBalances() {
        String sender = tokenClient.getSdkClient().getOperatorId().toString();

        // verify balances response contains sender, recipient and new token id
        MirrorBalancesResponse mirrorBalancesResponse = mirrorClient.getAccountBalances(sender);

        // verify response is not null
        assertNotNull(mirrorBalancesResponse);

        // verify valid set of balances
        List<MirrorCryptoBalance> accountBalances = mirrorBalancesResponse.getBalances();
        assertNotNull(accountBalances);
        assertThat(accountBalances).isNotEmpty();

        // verify valid balance object
        MirrorCryptoBalance mirrorCryptoBalance = accountBalances.get(0);
        assertNotNull(mirrorCryptoBalance);

        // verify sender is present
        assertThat(mirrorCryptoBalance.getAccount()).isEqualTo(sender);
        assertThat(mirrorCryptoBalance.getBalance()).isGreaterThan(0);

        // verify valid set of token balances
        List<MirrorTokenAccountBalance> tokenBalances = mirrorCryptoBalance.getTokens();
        assertNotNull(tokenBalances);
        assertThat(tokenBalances).isNotEmpty();
    }

    private MirrorTransaction verifyTransactions(int status) {
        String transactionId = networkTransactionResponse.getTransactionIdString();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        return mirrorTransaction;
    }

    private MirrorTokenResponse verifyToken() {
        MirrorTokenResponse mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());

        return mirrorToken;
    }

    private void verifyTokenTransfers() {
        String transactionId = networkTransactionResponse.getTransactionIdString();
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

    private void verifyTokenUpdate() {
        MirrorTokenResponse mirrorToken = verifyToken();

        assertThat(mirrorToken.getCreatedTimestamp()).isNotEqualTo(mirrorToken.getModifiedTimestamp());
    }

    /**
     * Recover method for token transaction retry operations. Method parameters of retry method must match this method
     * after exception parameter
     *
     * @param t
     */
    @Recover
    public void recover(StatusRuntimeException t) {
        log.error("Transaction submissions for token transaction failed after retries w: {}", t.getMessage());
        throw t;
    }

    /**
     * Recover method for REST verify operations. Method parameters of retry method must match this method after
     * exception parameter
     *
     * @param t
     */
    @Recover
    public void recover(AssertionError t) {
        log.error("REST API response verification failed after {} retries w: {}",
                acceptanceProps.getRestPollingProperties().getMaxAttempts(), t.getMessage());
        throw t;
    }
}
