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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleInfo;
import com.hedera.hashgraph.sdk.ScheduleInfoQuery;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.proto.TokenFreezeStatus;
import com.hedera.hashgraph.sdk.proto.TokenKycStatus;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.ScheduleClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorScheduleResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Cucumber
public class ScheduleFeature {
    private final static int DEFAULT_TINY_HBAR = 1_000;

    @Autowired
    private AcceptanceTestProperties acceptanceProps;
    @Autowired
    private ScheduleClient scheduleClient;
    @Autowired
    private AccountClient accountClient;
    @Autowired
    private TopicClient topicClient;
    @Autowired
    private TokenClient tokenClient;

    private ScheduleId scheduleId;

    @Autowired
    private MirrorNodeClient mirrorClient;

    private NetworkTransactionResponse networkTransactionResponse;

    private ExpandedAccountId additionalAccount;

    private ScheduleInfo scheduleInfo;

    private Transaction scheduledTransaction;

    private int expectedSignersCount;

    @Given("I successfully schedule a treasury disbursement")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewHBarTransferSchedule() throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 2;
        additionalAccount = accountClient.getReceiverSigRequiredAccount(0);
        scheduledTransaction = accountClient
                .getCryptoTransferTransaction(
                        accountClient.getTokenTreasuryAccount().getAccountId(),
                        additionalAccount.getAccountId(),
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR));

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I successfully schedule a crypto account create")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewCryptoAccountSchedule() throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 1;
        additionalAccount = accountClient.getReceiverSigNotRequiredAccount(0);
        scheduledTransaction = accountClient
                .getAccountCreateTransaction(
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR),
                        accountClient.getTokenTreasuryAccount().getPublicKey(),
                        false);

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I successfully schedule a crypto account create with {int} initial signatures")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewCryptoAccountSchedule(int initSignatureCount) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 1 + initSignatureCount;
        additionalAccount = accountClient.getReceiverSigNotRequiredAccount(0);

        KeyList privateKeyList = new KeyList();
        KeyList publicKeyList = new KeyList();
        for (int i = 0; i < initSignatureCount; i++) {
            PrivateKey accountKey = PrivateKey.generate();
            privateKeyList.add(accountKey);
            publicKeyList.add(accountKey.getPublicKey());
        }

        scheduledTransaction = accountClient
                .getAccountCreateTransaction(
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR),
                        publicKeyList, // set required signers
                        false);

        createNewSchedule(scheduledTransaction, privateKeyList);
    }

    @Given("I successfully schedule a token transfer")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewTokenTransferSchedule() throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 2;
        additionalAccount = accountClient.getReceiverSigRequiredAccount(0);

        // create token
        PrivateKey tokenKey = PrivateKey.generate();
        PublicKey tokenPublicKey = tokenKey.getPublicKey();
        log.debug("Token creation PrivateKey : {}, PublicKey : {}", tokenKey, tokenPublicKey);

        networkTransactionResponse = tokenClient.createToken(
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                accountClient.getTokenTreasuryAccount(),
                DEFAULT_TINY_HBAR);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);

        // associate new account, treasury is auto associated
        log.debug("Associate new account : {} with token {}", additionalAccount.getAccountId(), tokenId);
        networkTransactionResponse = tokenClient.asssociate(additionalAccount, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());

        Hbar hbarAmount = Hbar.fromTinybars(DEFAULT_TINY_HBAR);
        scheduledTransaction = tokenClient
                .getTokenTransferTransaction(
                        tokenId,
                        accountClient.getTokenTreasuryAccount().getAccountId(),
                        additionalAccount.getAccountId(),
                        10)
                // add Hbar transfer logic
                .addHbarTransfer(additionalAccount.getAccountId(), hbarAmount.negated())
                .addHbarTransfer(accountClient.getTokenTreasuryAccount().getAccountId(), hbarAmount);

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I successfully schedule a topic message submit")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewHCSSchedule() throws ReceiptStatusException, PrecheckStatusException, TimeoutException {
        expectedSignersCount = 1;
        additionalAccount = accountClient.getReceiverSigNotRequiredAccount(0);

        // create topic w submit key
        log.debug("Create new topic with a submit key");
        networkTransactionResponse = topicClient
                .createTopic(accountClient.getTokenTreasuryAccount(), additionalAccount.getPublicKey());
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TopicId topicId = networkTransactionResponse.getReceipt().topicId;
        assertNotNull(topicId);

        scheduledTransaction = topicClient
                .getTopicMessageSubmitTransaction(
                        topicId,
                        "scheduled hcs message".getBytes(StandardCharsets.UTF_8));

        createNewSchedule(scheduledTransaction, null);
    }

    private void createNewSchedule(Transaction transaction, KeyList signatureKeyList) throws PrecheckStatusException,
            ReceiptStatusException, TimeoutException {
        log.debug("Schedule creation ");

        // create signatures list
        networkTransactionResponse = scheduleClient.createSchedule(
                scheduleClient.getSdkClient().getExpandedOperatorAccountId(),
                transaction,
                "New Mirror Acceptance Schedule",
                signatureKeyList);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        scheduleId = networkTransactionResponse.getReceipt().scheduleId;
        assertNotNull(scheduleId);
    }

    @Then("the scheduled transaction is signed")
    public void signSignature() throws PrecheckStatusException, ReceiptStatusException, TimeoutException {
        networkTransactionResponse = scheduleClient.signSchedule(
                additionalAccount,
                scheduledTransaction,
                scheduleId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the scheduled transaction is signed by the additionalAccount")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void additionalAccountSignsSignature() throws PrecheckStatusException, ReceiptStatusException,
            TimeoutException {
        log.debug("additionalAccount signs scheduledTransaction");
        networkTransactionResponse = scheduleClient.signSchedule(
                additionalAccount,
                scheduledTransaction,
                scheduleId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        scheduleInfo = new ScheduleInfoQuery()
                .setScheduleId(scheduleId)
                .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                .execute(scheduleClient.getClient());
        log.debug("schedule {} signatories count: {}", scheduleInfo.scheduleId, scheduleInfo.signatories.size());
    }

    @Then("the scheduled transaction is signed by the tokenTreasuryAccount")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void tokenTreasuryAccountSignsSignature() throws PrecheckStatusException, ReceiptStatusException,
            TimeoutException {
        log.debug("tokenTreasuryAccount signs scheduledTransaction");
        networkTransactionResponse = scheduleClient.signSchedule(
                accountClient.getTokenTreasuryAccount(),
                scheduledTransaction,
                scheduleId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        scheduleInfo = new ScheduleInfoQuery()
                .setScheduleId(scheduleId)
                .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                .execute(scheduleClient.getClient());
        log.debug("schedule {} signatories count: {}", scheduleInfo.scheduleId, scheduleInfo.signatories.size());
    }

    @When("I successfully delete the schedule")
    public void deleteSchedule() throws PrecheckStatusException, ReceiptStatusException, TimeoutException {
        networkTransactionResponse = scheduleClient.deleteSchedule(scheduleId);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        scheduleInfo = null;
    }

    @Then("the network confirms schedule presence")
    public void verifyNetworkScheduleResponse() throws TimeoutException, PrecheckStatusException {
        scheduleInfo = new ScheduleInfoQuery()
                .setScheduleId(scheduleId)
                .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                .execute(scheduleClient.getClient());

        assertNotNull(scheduleInfo);
        assertThat(scheduleInfo.scheduleId).isEqualTo(scheduleId);
        assertThat(scheduleInfo.adminKey)
                .isEqualTo(accountClient.getSdkClient().getExpandedOperatorAccountId().getPublicKey());
        assertThat(scheduleInfo.payerAccountId)
                .isEqualTo(accountClient.getSdkClient().getExpandedOperatorAccountId().getAccountId());
    }

    @Then("the network confirms the schedule is not present")
    public void verifyNetworkScheduleNotPresentResponse() throws TimeoutException, PrecheckStatusException {
        boolean invalidSchedule = false;
        try {
            scheduleInfo = new ScheduleInfoQuery()
                    .setScheduleId(scheduleId)
                    .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                    .execute(scheduleClient.getClient());
        } catch (RuntimeException ex) {
            assertThat(ex).hasCauseInstanceOf(IllegalArgumentException.class);
            assertThat(ex).hasMessageContaining("INVALID_SCHEDULE_ID");
            invalidSchedule = true;
        }

        assertThat(invalidSchedule).isTrue();
        log.info("Schedule {} no longer returned from network state", scheduleId);
    }

    @Then("the network confirms all signers have provided their signatures")
    public void verifyNetworkCompleteSchedule() throws TimeoutException, PrecheckStatusException {
        scheduleInfo = new ScheduleInfoQuery()
                .setScheduleId(scheduleId)
                .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                .execute(scheduleClient.getClient());

        assertNotNull(scheduleInfo);
        assertThat(scheduleInfo.scheduleId).isEqualTo(scheduleId);
        log.debug("schedule signatories count: {}", scheduleInfo.signatories.size());
        assertThat(scheduleInfo.signatories).isNotEmpty();
        assertThat(scheduleInfo.signatories.size()).isEqualTo(scheduleInfo.signatories.threshold);
    }

    @Then("the mirror node REST API should return status {int} for the schedule transaction")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPIResponses(int status) {
        verifyTransactions(status);
//        verifySchedulePresence();
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

    private MirrorScheduleResponse verifySchedulePresence() {
        MirrorScheduleResponse mirrorSchedule = mirrorClient.getScheduleInfo(scheduleId.toString());

        assertNotNull(mirrorSchedule);
        assertThat(mirrorSchedule.getScheduleId()).isEqualTo(scheduleId.toString());

        assertThat(mirrorSchedule.getSignatures().size()).isEqualTo(scheduleInfo.signatories.size());

        // if signature count is expected verify executed timestamp was updated
        if (scheduleInfo.signatories.size() == expectedSignersCount) {
            assertThat(mirrorSchedule.getExecutedTimestamp()).isNotNull();
        } else {

            assertThat(mirrorSchedule.getExecutedTimestamp()).isNull();
        }

        return mirrorSchedule;
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
}
