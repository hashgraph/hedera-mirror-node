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

import com.google.common.base.Splitter;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import junit.framework.AssertionFailedError;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.CustomFee;
import com.hedera.hashgraph.sdk.CustomFixedFee;
import com.hedera.hashgraph.sdk.CustomFractionalFee;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorAssessedCustomFee;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTokenTransfer;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTokenResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Cucumber
public class TokenFeature {
    private static final int INITIAL_SUPPLY = 1_000_000;
    private static final Splitter ASSESSED_CUSTOM_FEES_SPLITTER = Splitter.on(';').trimResults().omitEmptyStrings();
    private static final Splitter ASSESSED_CUSTOM_FEE_SPLITTER = Splitter.on(',').trimResults();
    private static final Splitter CUSTOM_FEES_SPLITTER = ASSESSED_CUSTOM_FEES_SPLITTER;
    private static final Splitter CUSTOM_FEE_SPLITTER = ASSESSED_CUSTOM_FEE_SPLITTER;
    private static final Splitter FRACTION_SPLITTER = Splitter.on('/').trimResults();

    @Autowired
    private TokenClient tokenClient;
    @Autowired
    private AccountClient accountClient;
    @Autowired
    private MirrorNodeClient mirrorClient;
    @Autowired
    private TopicClient topicClient;
    private final List<TokenId> tokenIds = new ArrayList<>();
    private final List<ExpandedAccountId> senders = new ArrayList<>();
    private final List<ExpandedAccountId> recipients = new ArrayList<>();
    private NetworkTransactionResponse networkTransactionResponse;

    @Given("^I successfully create a new token(?: with custom fees (.*))?")
    public void createNewToken(String customFees) {
        createNewToken(
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                parseCustomFees(customFees)
        );
    }

    @Given("I successfully create a new token with freeze status {int} and kyc status {int}")
    public void createNewToken(int freezeStatus, int kycStatus) {
        createNewToken(RandomStringUtils.randomAlphabetic(4).toUpperCase(), freezeStatus, kycStatus);
    }

    @Given("^I associate a new sender account with token(?: (.*))?$")
    public void associateSenderWithToken(Integer tokenIndex) {
        ExpandedAccountId sender = accountClient.createNewAccount(10_000_000);
        associateWithToken(sender, tokenIds.get(getIndexOrDefault(tokenIndex)));
        senders.add(sender);
    }

    @Given("^I associate a new recipient account with token(?: (.*))?$")
    public void associateRecipientWithToken(Integer tokenIndex) {
        ExpandedAccountId recipient = accountClient.createNewAccount(10_000_000);
        associateWithToken(recipient, tokenIds.get(getIndexOrDefault(tokenIndex)));
        recipients.add(recipient);
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
        transferTokens(tokenIds.get(getIndexOrDefault(tokenIndex)), amount, payer,
                recipients.get(getIndexOrDefault(recipientIndex)).getAccountId());
    }

    @Then("^I transfer (.*) tokens (?:(.*) )?to sender(?: (.*))?$")
    public void transferTokensToSender(int amount, Integer tokenIndex, Integer senderIndex) {
        ExpandedAccountId payer = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        transferTokens(tokenIds.get(getIndexOrDefault(tokenIndex)), amount, payer,
                senders.get(getIndexOrDefault(senderIndex)).getAccountId());
    }

    @Given("^I update the token(?: (.*))?$")
    public void updateToken(Integer index) {
        networkTransactionResponse = tokenClient.updateToken(tokenIds.get(getIndexOrDefault(index)),
                tokenClient.getSdkClient().getExpandedOperatorAccountId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I burn {int} from the token")
    public void burnToken(int amount) {
        networkTransactionResponse = tokenClient.burn(tokenIds.get(0), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I mint {int} from the token")
    public void mintToken(int amount) {
        networkTransactionResponse = tokenClient.mint(tokenIds.get(0), amount);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I wipe {int} from the token")
    public void wipeToken(int amount) {
        networkTransactionResponse = tokenClient.wipe(tokenIds.get(0), amount, recipients.get(0));
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
        networkTransactionResponse = tokenClient
                .delete(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenIds.get(0));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenIds.remove(0);
    }

    @Given("I update token {int} with new custom fees schedule {string}")
    public void updateTokenFeeSchedule(int tokenIndex, String customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient
                .updateTokenFeeSchedule(tokenIds.get(tokenIndex), admin, parseCustomFees(customFees));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should return status {int}")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPIResponses(int status) {
        verifyTransactions(status);

        publishBackgroundMessages();
    }

    @Then("^the mirror node REST API should return status (.*) for token fund flow(?: with assessed custom fees (.*))?$")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenFundFlow(int status, String assessedCustomFees) {
        TokenId tokenId = tokenIds.get(0);
        verifyTransactions(status, parseAssessedCustomFees(assessedCustomFees));
        verifyToken(tokenId);
        verifyTokenTransfers(tokenId);

        publishBackgroundMessages();
    }

    @Then("the mirror node REST API should confirm token update")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorTokenUpdateFlow() {
        verifyTokenUpdate(tokenIds.get(0));

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
        for (TokenId tokenId : tokenIds) {
            // a nonzero balance will result in a TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES error
            // not possible to wipe a treasury account as it results in CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT error
            // as a result to dissociate first delete token
            ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
            try {
                tokenClient.delete(admin, tokenId);
                dissociateAccounts(tokenId, List.of(admin));
                dissociateAccounts(tokenId, senders);
                dissociateAccounts(tokenId, recipients);
            } catch (Exception ex) {
                log.warn("Error cleaning up token {} and associations error: {}", tokenId, ex);
            }
        }

        tokenIds.clear();
        senders.clear();
        recipients.clear();
    }

    private void dissociateAccounts(TokenId tokenId, List<ExpandedAccountId> accountIds) {
        for (ExpandedAccountId accountId : accountIds) {
            try {
                tokenClient.dissociate(accountId, tokenId);
                log.info("Successfully dissociated account {} from token {}", accountId, tokenId);
            } catch (Exception ex) {
                log.warn("Error dissociating account {} from token {}, error: {}", accountId, tokenId, ex);
            }
        }
    }

    private void createNewToken(String symbol, int freezeStatus, int kycStatus) {
        createNewToken(symbol, freezeStatus, kycStatus, Collections.emptyList());
    }

    private void createNewToken(String symbol, int freezeStatus, int kycStatus, List<CustomFee> customFees) {
        ExpandedAccountId admin = tokenClient.getSdkClient().getExpandedOperatorAccountId();
        networkTransactionResponse = tokenClient.createToken(
                admin,
                symbol,
                freezeStatus,
                kycStatus,
                admin,
                INITIAL_SUPPLY,
                customFees);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);
        tokenIds.add(tokenId);
    }

    private void associateWithToken(ExpandedAccountId accountId, TokenId tokenId) {
        networkTransactionResponse = tokenClient.associate(accountId, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    private List<MirrorAssessedCustomFee> parseAssessedCustomFees(String assessedCustomFees) {
        if (Strings.isEmpty(assessedCustomFees)) {
            return Collections.emptyList();
        }

        List<MirrorAssessedCustomFee> assessedCustomFeeList = new ArrayList<>();
        for (String fee : ASSESSED_CUSTOM_FEES_SPLITTER.split(assessedCustomFees)) {
            MirrorAssessedCustomFee assessedCustomFee = new MirrorAssessedCustomFee();

            List<String> parts = ASSESSED_CUSTOM_FEE_SPLITTER.splitToList(fee);
            assessedCustomFee.setAmount(parts.get(0));

            int recipientIndex = Integer.parseInt(parts.get(1));
            assessedCustomFee.setCollectorAccountId(recipients.get(recipientIndex).getAccountId().toString());

            int senderIndex = Integer.parseInt(parts.get(2));
            assessedCustomFee.setPayerAccountId(senders.get(senderIndex).getAccountId().toString());

            String tokenIndex = parts.get(3);
            if (Strings.isNotEmpty(tokenIndex)) {
                assessedCustomFee.setTokenId(tokenIds.get(Integer.parseInt(tokenIndex)).toString());
            }

            assessedCustomFeeList.add(assessedCustomFee);
        }

        return assessedCustomFeeList;
    }

    private List<CustomFee> parseCustomFees(String customFees) {
        if (customFees == null) {
            return Collections.emptyList();
        }

        List<CustomFee> customFeeList = new ArrayList<>();
        for (String fee : CUSTOM_FEES_SPLITTER.split(customFees)) {
            List<String> parts = CUSTOM_FEE_SPLITTER.splitToList(fee);
            int partIndex = 0;
            String amount = parts.get(partIndex++);
            AccountId collector = recipients.get(Integer.parseInt(parts.get(partIndex++))).getAccountId();

            CustomFee customFee;
            if (amount.contains("/")) {
                // fractional fee
                CustomFractionalFee fractionalFee = new CustomFractionalFee();

                List<String> fractionComponents = FRACTION_SPLITTER.splitToList(amount);
                fractionalFee.setNumerator(Long.parseLong(fractionComponents.get(0)));
                fractionalFee.setDenominator(Long.parseLong(fractionComponents.get(1)));
                fractionalFee.setFeeCollectorAccountId(collector);

                String maximumAmount = parts.get(partIndex++);
                if (Strings.isNotEmpty(maximumAmount)) {
                    fractionalFee.setMax(Long.parseLong(maximumAmount));
                }

                String minimumAmount = parts.get(partIndex);
                if (Strings.isNotEmpty(minimumAmount)) {
                    fractionalFee.setMin(Long.parseLong(minimumAmount));
                }

                customFee = fractionalFee;
            } else {
                // fixed fee
                CustomFixedFee fixedFee = new CustomFixedFee();

                fixedFee.setAmount(Long.parseLong(amount));
                fixedFee.setFeeCollectorAccountId(collector);

                String tokenIndex = parts.get(partIndex);
                if (Strings.isNotEmpty(tokenIndex)) {
                    fixedFee.setDenominatingTokenId(tokenIds.get(Integer.parseInt(tokenIndex)));
                }

                customFee = fixedFee;
            }

            customFeeList.add(customFee);
        }

        return customFeeList;
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
        return verifyTransactions(status, Collections.emptyList());
    }

    private MirrorTransaction verifyTransactions(int status, List<MirrorAssessedCustomFee> assessedCustomFees) {
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

    private MirrorTokenResponse verifyToken(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = mirrorClient.getTokenInfo(tokenId.toString());

        assertNotNull(mirrorToken);
        assertThat(mirrorToken.getTokenId()).isEqualTo(tokenId.toString());

        return mirrorToken;
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

    private void verifyTokenUpdate(TokenId tokenId) {
        MirrorTokenResponse mirrorToken = verifyToken(tokenId);

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

    private int getIndexOrDefault(Integer index) {
        return Optional.ofNullable(index).orElse(0);
    }
}
