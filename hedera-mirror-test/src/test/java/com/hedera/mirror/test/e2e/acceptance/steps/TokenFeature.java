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

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenTransfer;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
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
    @Autowired
    private TopicClient topicClient;
    private PrivateKey tokenKey;
    private TokenId tokenId;
    private ExpandedAccountId sender;
    private ExpandedAccountId recipient;
    private NetworkTransactionResponse networkTransactionResponse;

    @Given("I successfully create a new token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewToken() throws ReceiptStatusException, PrecheckStatusException, TimeoutException {
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully create a new token {string}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewToken(String symbol) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {
        createNewToken(symbol, TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully onboard a new token account with freeze status {int} and kyc status {int}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewToken(int freezeStatus, int kycStatus) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);
    }

    @Given("I successfully onboard a new token account")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void onboardNewTokenAccount() throws PrecheckStatusException, ReceiptStatusException, TimeoutException {
        onboardNewTokenAccount(TokenFreezeStatus.FreezeNotApplicable_VALUE, TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I associate with token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void associateWithToken() throws ReceiptStatusException, PrecheckStatusException, TimeoutException {
        // associate payer
        sender = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        associateWithToken(sender);
    }

    @Given("I associate a new sender account with token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void associateSenderWithToken() throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        sender = accountClient.createNewAccount(10_000_000);
        associateWithToken(sender);
    }

    @Given("I associate a new recipient account with token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void associateRecipientWithToken() throws ReceiptStatusException, PrecheckStatusException, TimeoutException {

        recipient = accountClient.createNewAccount(10_000_000);
        associateWithToken(recipient);
    }

    @When("I set new account freeze status to {int}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void setFreezeStatus(int freezeStatus) throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        setFreezeStatus(freezeStatus, recipient);
    }

    @When("I set new account kyc status to {int}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void setKycStatus(int kycStatus) throws ReceiptStatusException, PrecheckStatusException, TimeoutException {
        setKycStatus(kycStatus, recipient);
    }

    @Then("I transfer {int} tokens to payer")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void fundPayerAccountWithTokens(int amount) throws PrecheckStatusException, ReceiptStatusException,
            TimeoutException {
        transferTokens(tokenId, amount, accountClient.getTokenTreasuryAccount(), tokenClient.getSdkClient()
                .getOperatorId());
    }

    @Then("I transfer {int} tokens to recipient")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void transferTokensToRecipient(int amount) throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        transferTokens(tokenId, amount, sender, recipient
                .getAccountId());
    }

    @Given("I update the token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void updateToken() throws PrecheckStatusException, ReceiptStatusException, TimeoutException {

        networkTransactionResponse = tokenClient
                .updateToken(tokenId, tokenClient.getSdkClient().getExpandedOperatorAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I burn {int} from the token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void burnToken(int amount) throws PrecheckStatusException, ReceiptStatusException, TimeoutException {

        networkTransactionResponse = tokenClient.burn(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint {int} from the token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void mintToken(int amount) throws PrecheckStatusException, ReceiptStatusException, TimeoutException {

        networkTransactionResponse = tokenClient.mint(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I wipe {int} from the token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void wipeToken(int amount) throws PrecheckStatusException, ReceiptStatusException, TimeoutException {

        networkTransactionResponse = tokenClient.wipe(tokenId, amount, recipient);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I dissociate the account from the token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void dissociateNewAccountFromToken() throws PrecheckStatusException, ReceiptStatusException,
            TimeoutException {
        networkTransactionResponse = tokenClient.disssociate(recipient, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the token")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void deleteToken() throws PrecheckStatusException, ReceiptStatusException, TimeoutException {

        networkTransactionResponse = tokenClient
                .delete(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should return status {int}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPIResponses(int status) throws PrecheckStatusException, ReceiptStatusException,
            TimeoutException {
        verifyTransactions(status);

        // publish background message to network to reduce possibility of stale info in low TPS environment
        topicClient.publishMessageToDefaultTopic();
    }

    @Then("the mirror node REST API should return status {int} for token fund flow")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenFundFlow(int status) throws PrecheckStatusException, ReceiptStatusException,
            TimeoutException {
        verifyTransactions(status);
        verifyToken();
        verifyTokenTransfers();

        // publish background message to network to reduce possibility of stale info in low TPS environment
        topicClient.publishMessageToDefaultTopic();
    }

    @Then("the mirror node REST API should confirm token update")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenUpdateFlow() throws PrecheckStatusException, ReceiptStatusException, TimeoutException {
        verifyTokenUpdate();

        // publish background message to network to reduce possibility of stale info in low TPS environment
        topicClient.publishMessageToDefaultTopic();
    }

    @Then("the mirror node REST API should return status {int} for transaction {string}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorRestTransactionIsPresent(int status, String transactionIdString) throws PrecheckStatusException, ReceiptStatusException, TimeoutException {
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionIdString);

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionIdString);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        // publish background message to network to reduce possibility of stale info in low TPS environment
        topicClient.publishMessageToDefaultTopic();
    }

    private void createNewToken(String symbol, int freezeStatus, int kycStatus) throws PrecheckStatusException,
            ReceiptStatusException, TimeoutException {
        tokenKey = PrivateKey.generate();
        PublicKey tokenPublicKey = tokenKey.getPublicKey();
        log.debug("Token creation PrivateKey : {}, PublicKey : {}", tokenKey, tokenPublicKey);

        networkTransactionResponse = tokenClient.createToken(
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                symbol,
                freezeStatus,
                kycStatus,
                accountClient.getTokenTreasuryAccount(),
                INITIAL_SUPPLY);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
    }

    private void associateWithToken(ExpandedAccountId accountId) throws PrecheckStatusException,
            ReceiptStatusException, TimeoutException {
        networkTransactionResponse = tokenClient.asssociate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setFreezeStatus(int freezeStatus, ExpandedAccountId accountId) throws PrecheckStatusException,
            ReceiptStatusException, TimeoutException {
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

    private void setKycStatus(int kycStatus, ExpandedAccountId accountId) throws PrecheckStatusException,
            ReceiptStatusException, TimeoutException {
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

    private void transferTokens(TokenId tokenId, int amount, ExpandedAccountId sender, AccountId receiver) throws PrecheckStatusException, ReceiptStatusException, TimeoutException {
        networkTransactionResponse = tokenClient.transferToken(tokenId, sender, receiver, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void onboardNewTokenAccount(int freezeStatus, int kycStatus) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {
        // create token, associate payer and transfer tokens to payer
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);

        associateWithToken();

        fundPayerAccountWithTokens(INITIAL_SUPPLY / 2);
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
    public void recover(PrecheckStatusException t) throws PrecheckStatusException {
        log.error("Transaction submissions for token transaction failed after retries w: {}", t.getMessage());
        throw t;
    }

    /**
     * Recover method for token transaction retry operations. Method parameters of retry method must match this method
     * after exception parameter
     *
     * @param t
     */
    @Recover
    public void recover(PrecheckStatusException t, int count) throws PrecheckStatusException {
        log.error("Transaction submissions for token transaction failed after retries w: {}", t.getMessage());
        throw t;
    }

    /**
     * Recover method for token transaction retry operations. Method parameters of retry method must match this method
     * after exception parameter
     *
     * @param t
     */
    @Recover
    public void recover(PrecheckStatusException t, String param) throws PrecheckStatusException {
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

    @After("@TokenBase")
    public void closeClients() {
        log.debug("Closing token feature clients");
        accountClient.close();
        mirrorClient.close();
        tokenClient.close();
    }
}
