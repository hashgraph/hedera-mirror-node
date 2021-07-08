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
import junit.framework.AssertionFailedError;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;
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
    public void createNewToken() {
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully create a new token {string}")
    public void createNewToken(String symbol) throws Exception {
        createNewToken(symbol, TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully create a new token account with freeze status {int} and kyc status {int}")
    public void createNewToken(int freezeStatus, int kycStatus) {
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);
    }

    @Given("I associate with token")
    public void associateWithToken() throws Exception {
        // associate payer
        sender = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        associateWithToken(sender);
    }

    @Given("I associate a new sender account with token")
    public void associateSenderWithToken() {

        sender = accountClient.createNewAccount(10_000_000);
        associateWithToken(sender);
    }

    @Given("I associate a new recipient account with token")
    public void associateRecipientWithToken() {

        recipient = accountClient.createNewAccount(10_000_000);
        associateWithToken(recipient);
    }

    @When("I set new account freeze status to {int}")
    public void setFreezeStatus(int freezeStatus) {
        setFreezeStatus(freezeStatus, recipient);
    }

    @When("I set new account kyc status to {int}")
    public void setKycStatus(int kycStatus) {
        setKycStatus(kycStatus, recipient);
    }

    @Then("I transfer {int} tokens to payer")
    public void fundPayerAccountWithTokens(int amount) {
        transferTokens(tokenId, amount, recipient, tokenClient.getSdkClient().getExpandedOperatorAccountId()
                .getAccountId());
    }

    @Then("I transfer {int} tokens to recipient")
    public void transferTokensToRecipient(int amount) {
        transferTokens(tokenId, amount, sender, recipient
                .getAccountId());
    }

    @Given("I update the token")
    public void updateToken() {

        networkTransactionResponse = tokenClient
                .updateToken(tokenId, tokenClient.getSdkClient().getExpandedOperatorAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I burn {int} from the token")
    public void burnToken(int amount) {

        networkTransactionResponse = tokenClient.burn(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint {int} from the token")
    public void mintToken(int amount) {

        networkTransactionResponse = tokenClient.mint(tokenId, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I wipe {int} from the token")
    public void wipeToken(int amount) {

        networkTransactionResponse = tokenClient.wipe(tokenId, amount, recipient);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I dissociate the account from the token")
    public void dissociateNewAccountFromToken() {
        networkTransactionResponse = tokenClient.dissociate(recipient, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I delete the token")
    public void deleteToken() {

        networkTransactionResponse = tokenClient
                .delete(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenId = null;
    }

    @Then("the mirror node REST API should return status {int}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPIResponses(int status) {
        verifyTransactions(status);

        publishBackgroundMessages();
    }

    @Then("the mirror node REST API should return status {int} for token fund flow")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenFundFlow(int status) {
        verifyTransactions(status);
        verifyToken();
        verifyTokenTransfers();

        publishBackgroundMessages();
    }

    @Then("the mirror node REST API should confirm token update")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenUpdateFlow() throws Exception {
        verifyTokenUpdate();

        // publish background message to network to reduce possibility of stale info in low TPS environment
        topicClient.publishMessageToDefaultTopic();
    }

    @Then("the mirror node REST API should return status {int} for transaction {string}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
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

        // publish background message to network to reduce possibility of stale info in low TPS environment
        topicClient.publishMessageToDefaultTopic();
    }

    @After
    public void cleanup() {
        // dissociate all applicable accounts from token to reduce likelihood of max token association error
        if (tokenId != null) {
            // a nonzero balance will result in a TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES error
            // not possible to wipe a treasury account as it results in CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT error
            // as a result to dissociate first delete token
            try {
                tokenClient.delete(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenId);
                dissociateAccount(sender);
                dissociateAccount(recipient);
                tokenId = null;
            } catch (Exception ex) {
                log.warn("Error cleaning up token {} and associations error: {}", tokenId, ex);
            }
        }
    }

    private void dissociateAccount(ExpandedAccountId accountId) {
        if (accountId != null) {
            try {
                tokenClient.dissociate(accountId, tokenId);
                log.info("Successfully dissociated account {} from token {}", accountId, tokenId);
            } catch (Exception ex) {
                log.warn("Error dissociating account {} from token {}, error: {}", accountId, tokenId, ex);
            }
        }
    }

    private void createNewToken(String symbol, int freezeStatus, int kycStatus) {
        tokenKey = PrivateKey.generate();
        PublicKey tokenPublicKey = tokenKey.getPublicKey();
        log.trace("Token creation PrivateKey : {}, PublicKey : {}", tokenKey, tokenPublicKey);

        sender = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.createToken(
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                symbol,
                freezeStatus,
                kycStatus,
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                INITIAL_SUPPLY);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
    }

    private void associateWithToken(ExpandedAccountId accountId) {
        networkTransactionResponse = tokenClient.associate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private void setFreezeStatus(int freezeStatus, ExpandedAccountId accountId) {
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

    private void setKycStatus(int kycStatus, ExpandedAccountId accountId) {
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

    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    private void transferTokens(TokenId tokenId, int amount, ExpandedAccountId sender, AccountId receiver) {
        long startingBalance = tokenClient.getTokenBalance(receiver, tokenId);
        networkTransactionResponse = tokenClient.transferToken(tokenId, sender, receiver, amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        assertThat(tokenClient.getTokenBalance(receiver, tokenId)).isEqualTo(startingBalance + amount);
    }

    private MirrorTransaction verifyTransactions(int status) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
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

    private void verifyTokenUpdate() {
        MirrorTokenResponse mirrorToken = verifyToken();

        assertThat(mirrorToken.getCreatedTimestamp()).isNotEqualTo(mirrorToken.getModifiedTimestamp());
    }

    private void publishBackgroundMessages() {
        // publish background message to network to reduce possibility of stale info in low TPS environment
        try {
            topicClient.publishMessageToDefaultTopic();
        } catch (Exception ex) {
            log.trace("Encountered issue published background messages to default topic", ex);
        }
    }
}
