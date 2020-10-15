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

import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import io.grpc.StatusRuntimeException;
import java.time.Instant;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.proto.TokenFreezeStatus;
import com.hedera.hashgraph.proto.TokenKycStatus;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;

@Log4j2
@Cucumber
public class TokenFeature {
    @Autowired
    private AcceptanceTestProperties acceptanceProps;
    @Autowired
    private TokenClient tokenClient;
    @Autowired
    private AccountClient accountClient;
    private Instant testInstantReference;
    private Ed25519PrivateKey tokenKey;
    private String symbol;
    private TokenId tokenId;
    private ExpandedAccountId recipient;

    @Given("I successfully create a new token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewToken() throws HederaStatusException {
        testInstantReference = Instant.now();

        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE);
    }

    @Given("I successfully create a new token with freeze status {int} and kyc status {int}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewToken(int freezeStatus, int kycStatus) throws HederaStatusException {
        testInstantReference = Instant.now();

        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);
    }

    @Given("I successfully create a new token {string}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void createNewToken(String symbol, int freezeStatus, int kycStatus) throws HederaStatusException {
        testInstantReference = Instant.now();

        tokenKey = Ed25519PrivateKey.generate();
        Ed25519PublicKey tokenPublicKey = tokenKey.publicKey;
        log.debug("Token creation PrivateKey : {}, PublicKey : {}", tokenKey, tokenPublicKey);

        TransactionReceipt receipt = tokenClient
                .createToken(tokenClient.getSdkClient()
                        .getPayerPublicKey(), symbol, freezeStatus, kycStatus);
        assertNotNull(receipt);
        tokenId = receipt.getTokenId();
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

        TransactionReceipt receipt = tokenClient
                .asssociate(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenId);
        assertNotNull(receipt);
    }

    @Given("I associate a new account with token")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void associateNewAccountWithToken() throws HederaStatusException {

        recipient = accountClient.createNewAccount(10_000_000);
        TransactionReceipt receipt = tokenClient
                .asssociate(recipient, tokenId);
        assertNotNull(receipt);
    }

    @When("I set new account freeze status to {int}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void setFreezeStatus(int freezeStatus) throws HederaStatusException {
        TransactionReceipt receipt = null;
        if (freezeStatus == TokenFreezeStatus.Frozen_VALUE) {
            receipt = tokenClient.freeze(tokenId, recipient.getAccountId(), tokenKey);
        } else if (freezeStatus == TokenFreezeStatus.Unfrozen_VALUE) {
            receipt = tokenClient.unfreeze(tokenId, recipient.getAccountId(), tokenKey);
        } else {
            log.warn("Freeze Status must be set to 1 (Frozen) or 2 (Unfrozen)");
        }

        assertNotNull(receipt);
    }

    @When("I set new account kyc status to {int}")
    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void setKycStatus(int kycStatus) throws HederaStatusException {
        TransactionReceipt receipt = null;
        if (kycStatus == TokenKycStatus.Granted_VALUE) {
            receipt = tokenClient.grantKyc(tokenId, recipient.getAccountId(), tokenKey);
        } else if (kycStatus == TokenKycStatus.Revoked_VALUE) {
            receipt = tokenClient.revokeKyc(tokenId, recipient.getAccountId(), tokenKey);
        } else {
            log.warn("Kyc Status must be set to 1 (Granted) or 2 (Revoked)");
        }

        assertNotNull(receipt);
    }

    @Then("I transfer {int} tokens to recipient")
    public void transferTokensToRecipient(int amount) throws HederaStatusException {
        transferTokens(tokenId, amount, tokenClient.getSdkClient().getOperatorId(), recipient.getAccountId());
    }

    @Then("I transfer {int} tokens of {int} to {int}")
    public void transferTokens(int amount, int token, int recipient) throws HederaStatusException {
        transferTokens(new TokenId(token), amount, tokenClient.getSdkClient()
                .getOperatorId(), new AccountId(recipient));
    }

    @Retryable(value = {StatusRuntimeException.class}, exceptionExpression = "#{message.contains('UNAVAILABLE') || " +
            "message.contains('RESOURCE_EXHAUSTED')}")
    public void transferTokens(TokenId tokenId, int amount, AccountId sender, AccountId receiver) throws HederaStatusException {
        TransactionReceipt receipt = tokenClient.transferToken(tokenId, sender, receiver, amount);
        assertNotNull(receipt);
    }

    @Then("the network should observe an error creating a token {string}")
    public void verifyTokenCreation(String errorCode) throws Throwable {
        try {
            TransactionReceipt receipt = tokenClient.createToken(tokenClient.getSdkClient()
                            .getPayerPublicKey(), symbol, TokenFreezeStatus.FreezeNotApplicable_VALUE,
                    TokenKycStatus.KycNotApplicable_VALUE);
            assertTrue(errorCode.isEmpty());
            assertNotNull(receipt);
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
            TransactionReceipt receipt = tokenClient
                    .asssociate(accountToAssociate, tokenId);
            assertNotNull(receipt);
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
}
