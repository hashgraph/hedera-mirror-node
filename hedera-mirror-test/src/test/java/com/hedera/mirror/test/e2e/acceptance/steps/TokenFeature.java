package com.hedera.mirror.test.e2e.acceptance.steps;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import java.util.ArrayList;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.reactive.function.client.ClientResponse;

import com.hedera.hashgraph.proto.TokenFreezeStatus;
import com.hedera.hashgraph.proto.TokenKycStatus;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorAccountBalance;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorCryptoBalance;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenBalance;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenTransfer;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorBalancesResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenBalancesResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Cucumber
public class TokenFeature {
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
    private ExpandedAccountId recipient;
    private NetworkTransactionResponse networkTransactionResponse;
    private List<TransactionId> transactionIdList;

    @Given("I successfully create a new token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewToken() throws HederaStatusException {
        testInstantReference = Instant.now();

        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully create a new token {string}")
    public void createNewToken(String symbol) throws HederaStatusException {
        testInstantReference = Instant.now();

        createNewToken(symbol, TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully create a new token with freeze status {int} and kyc status {int}")
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

        networkTransactionResponse = tokenClient
                .createToken(tokenClient.getSdkClient()
                        .getExpandedOperatorAccountId(), symbol, freezeStatus, kycStatus);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenId = networkTransactionResponse.getReceipt().getTokenId();
        assertNotNull(tokenId);
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

        networkTransactionResponse = tokenClient
                .asssociate(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I associate a new account with token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void associateNewAccountWithToken() throws HederaStatusException {

        recipient = accountClient.createNewAccount(10_000_000);
        networkTransactionResponse = tokenClient
                .asssociate(recipient, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I set new account freeze status to {int}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void setFreezeStatus(int freezeStatus) throws HederaStatusException {
        if (freezeStatus == TokenFreezeStatus.Frozen_VALUE) {
            networkTransactionResponse = tokenClient.freeze(tokenId, recipient.getAccountId(), tokenKey);
        } else if (freezeStatus == TokenFreezeStatus.Unfrozen_VALUE) {
            networkTransactionResponse = tokenClient.unfreeze(tokenId, recipient.getAccountId(), tokenKey);
        } else {
            log.warn("Freeze Status must be set to 1 (Frozen) or 2 (Unfrozen)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I set new account kyc status to {int}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void setKycStatus(int kycStatus) throws HederaStatusException {
        if (kycStatus == TokenKycStatus.Granted_VALUE) {
            networkTransactionResponse = tokenClient.grantKyc(tokenId, recipient.getAccountId(), tokenKey);
        } else if (kycStatus == TokenKycStatus.Revoked_VALUE) {
            networkTransactionResponse = tokenClient.revokeKyc(tokenId, recipient.getAccountId(), tokenKey);
        } else {
            log.warn("Kyc Status must be set to 1 (Granted) or 2 (Revoked)");
        }

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("I transfer {int} tokens to recipient")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void transferTokensToRecipient(int amount) throws HederaStatusException {
        transferTokens(tokenId, amount, tokenClient.getSdkClient().getOperatorId(), recipient.getAccountId());
    }

    @Then("I transfer {int} tokens of {int} to {int}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void transferTokens(int amount, int token, int recipient) throws HederaStatusException {
        transferTokens(new TokenId(token), amount, tokenClient.getSdkClient()
                .getOperatorId(), new AccountId(recipient));
    }

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void transferTokens(TokenId tokenId, int amount, AccountId sender, AccountId receiver) throws HederaStatusException {
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
    @Retryable(value = {AssertionError.class, AssertionFailedError.class}, backoff = @Backoff(delay = 5000))
    public void verifyMirrorAPIResponses(int status) {
        verifyTransactions(status);
    }

    @Then("the mirror node REST API should return status {int} for token fund flow")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class}, backoff = @Backoff(delay = 5000))
    public void verifyMirrorTokenFundFlow(int status) {
        verifyBalances(status);
        verifyTransactions(status);
        verifyToken(status);
    }

    @Then("the mirror node REST API should return status {int} for token update")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class}, backoff = @Backoff(delay = 5000))
    public void verifyMirrorTokenUpdateFlow(int status) {
        verifyTokenUpdate(status);
    }

    @Then("the mirror node {string} REST API should return status {int}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class}, exceptionExpression = "#{message.contains" +
            "('Expecting') || " +
            "message.contains('to contain')}", backoff = @Backoff(delay = 2000))
    public void verifyMirrorTransactionAPIResponse(String endpoint, int status) throws Throwable {
        if (endpoint.equalsIgnoreCase("balances")) {
            verifyBalances(status);
        } else if (endpoint.equalsIgnoreCase("transaction")) {
            verifyTransactions(status);
        } else if (endpoint.equalsIgnoreCase("token")) {
            verifyToken(status);
        } else if (endpoint.equalsIgnoreCase("tokenBalance")) {
            verifyTokenBalance(status);
        } else if (endpoint.equalsIgnoreCase("tokenTransfer")) {
            verifyTokenTransfers(status);
        } else if (endpoint.equalsIgnoreCase("tokenUpdate")) {
//            verifyTokenTransfers(status);
        }
    }

    @Then("the mirror node REST API should return status {int} for transaction {string}")
    public void verifyMirrorRestTransactionIsPresent(int status, String transactionIdString) throws Throwable {
        ClientResponse response = mirrorClient.verifyTransactionRestEntity(transactionIdString);

        verifyRESTResponse(status, response);
        MirrorTransactionsResponse mirrorTransactionsResponse = response.bodyToMono(MirrorTransactionsResponse.class)
                .block();

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionIdString);
    }

    @Then("the network should observe an error creating a token {string}")
    public void verifyTokenCreation(String errorCode) throws Throwable {
        try {
            networkTransactionResponse = tokenClient.createToken(tokenClient.getSdkClient()
                            .getExpandedOperatorAccountId(), symbol, TokenFreezeStatus.FreezeNotApplicable_VALUE,
                    TokenKycStatus.KycNotApplicable_VALUE);
            assertNotNull(networkTransactionResponse.getTransactionId());
            TransactionReceipt receipt = networkTransactionResponse.getReceipt();
            assertNotNull(receipt);
            assertTrue(errorCode.isEmpty());
            TokenId tokenId = receipt.getTokenId();
            assertNotNull(tokenId);
        } catch (Exception ex) {
            if (!ex.getMessage().contains(errorCode)) {
                log.info("Exception mismatch : {}", ex.getMessage());
                throw new Exception("Unexpected error code returned");
            } else {
                log.warn("Expected error found");
            }
        }
    }

    @Then("the network should observe an error associating a token {string}")
    public void verifyTokenAssociation(String errorCode) throws Throwable {
        try {
            ExpandedAccountId accountToAssociate = recipient == null ? tokenClient.getSdkClient()
                    .getExpandedOperatorAccountId() : recipient;
            networkTransactionResponse = tokenClient
                    .asssociate(accountToAssociate, tokenId);
            assertNotNull(networkTransactionResponse.getTransactionId());
            assertNotNull(networkTransactionResponse.getReceipt());
            assertTrue(errorCode.isEmpty());
        } catch (Exception ex) {
            if (!ex.getMessage().contains(errorCode)) {
                log.info("Exception mismatch : {}", ex.getMessage());
                throw new Exception("Unexpected error code returned");
            } else {
                log.warn("Expected error found");
            }
        }
    }

    private void verifyBalances(int status) {
        String sender = tokenClient.getSdkClient().getOperatorId().toString();
        ClientResponse response = mirrorClient.verifyAccountBalanceRestEndpoint(sender);

        // verify balances response contains sender, recipient and new token id
        verifyRESTResponse(status, response);
        MirrorBalancesResponse mirrorBalancesResponse = response.bodyToMono(MirrorBalancesResponse.class)
                .block();

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

        // verify valid set of token balances
        List<MirrorTokenBalance> tokenBalances = mirrorCryptoBalance.getTokens();
        assertNotNull(tokenBalances);
        assertThat(tokenBalances).isNotEmpty();

        // to:do when account balances are update with transactions verify new token is present in balance set
    }

    private void verifyTransactions(int status) {
        String transactionId = networkTransactionResponse.getTransactionIdString();
        ClientResponse response = mirrorClient.verifyTransactionRestEntity(transactionId);

        verifyRESTResponse(status, response);
        MirrorTransactionsResponse mirrorTransactionsResponse = response.bodyToMono(MirrorTransactionsResponse.class)
                .block();

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTime())
                .isEqualTo(networkTransactionResponse.getValidStartString());
    }

    private MirrorTokenResponse verifyToken(int status) {
        ClientResponse response = mirrorClient.verifyTokenInfoEndpoint(tokenId.toString());

        List<String> stringsToVerify = new ArrayList<>();
        stringsToVerify.add(tokenId.toString());

        verifyRESTResponse(status, response);

        MirrorTokenResponse mirrorToken = response.bodyToMono(MirrorTokenResponse.class)
                .block();

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());

        return mirrorToken;
    }

    private void verifyTokenBalance(int status) {
        String recipientString = recipient.getAccountId().toString();
        String sender = tokenClient.getSdkClient().getOperatorId().toString();
        ClientResponse response = mirrorClient
                .verifyTokenBalanceEndpoint(tokenId.toString(), recipientString);

        List<String> stringsToVerify = new ArrayList<>();
        stringsToVerify.add(recipientString);

        verifyRESTResponse(status, response);

        MirrorTokenBalancesResponse mirrorTokenBalancesResponse = response.bodyToMono(MirrorTokenBalancesResponse.class)
                .block();

        assertNotNull(mirrorTokenBalancesResponse);
        List<MirrorAccountBalance> balances = mirrorTokenBalancesResponse.getBalances();
        assertNotNull(balances);
        assertThat(balances).isNotEmpty();

        boolean recipientFound = false;
        boolean senderFound = false;

        for (MirrorAccountBalance balance : balances) {
            if (recipientFound && senderFound) {
                break;
            }

            if (balance.getAccount().equalsIgnoreCase(recipientString)) {
                recipientFound = true;
                continue;
            }

            if (balance.getAccount().equalsIgnoreCase(sender)) {
                senderFound = true;
                continue;
            }
        }

        assertTrue(recipientFound);
        assertTrue(senderFound);
    }

    private void verifyTokenTransfers(int status) {
        String transactionId = networkTransactionResponse.getTransactionIdString();
        ClientResponse response = mirrorClient.verifyTransactionRestEntity(transactionId);

        verifyRESTResponse(status, response);
        MirrorTransactionsResponse mirrorTransactionsResponse = response.bodyToMono(MirrorTransactionsResponse.class)
                .block();

        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTime())
                .isEqualTo(networkTransactionResponse.getValidStartString());

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

    private void verifyTokenUpdate(int status) {
        MirrorTokenResponse mirrorToken = verifyToken(status);

        assertThat(mirrorToken.getCreatedTimestamp()).isNotEqualTo(mirrorToken.getModifiedTimestamp());
    }

    private void verifyRESTResponse(int status, ClientResponse response) {
        assertNotNull(response, "httpStatus null");
        assertNotNull(response.statusCode(), "httpStatus null");
        assertEquals(status, response.statusCode().value(), "mirrorResponse matched");
    }

    /**
     * Recover method for REST verify operations. Method parameters of retry method must match this method after
     * exception parameter
     *
     * @param t
     */
    @Recover
    public void recover(AssertionError t, int status) {
        log.error("Received {} from REST API, failed verification after {} retries w: {}",
                status, acceptanceProps.getSubscribeRetries(), t.getMessage());
        throw t;
    }

    /**
     * Recover method for REST verify operations. Method parameters of retry method must match this method after
     * exception parameter
     *
     * @param t
     */
    @Recover
    public void recover(AssertionError t, String endpoint, int status) {
        log.error("Received {} response from {}. Failed verification after {} retries w:" +
                " {}", status, endpoint, acceptanceProps.getSubscribeRetries(), t.getMessage());
        throw t;
    }
}
