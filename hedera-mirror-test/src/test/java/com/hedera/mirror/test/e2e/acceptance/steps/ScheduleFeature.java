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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
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
    private TransactionId scheduleCreateTransactionId;

    private ScheduleInfo scheduleInfo;

    private Transaction scheduledTransaction;

    private int expectedSignersCount;
    private int currentSignersCount;
    private final int signatoryCountOffset = 1; // Schedule map includes payer account which may not be a required
    // signatory

    @Given("I successfully schedule a treasury HBAR disbursement to {string}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewHBarTransferSchedule(String accountName) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 2;
        currentSignersCount = 0 + signatoryCountOffset;
        ExpandedAccountId recipient = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(accountName)); // receiverSigRequired
        scheduledTransaction = accountClient
                .getCryptoTransferTransaction(
                        accountClient.getTokenTreasuryAccount().getAccountId(),
                        recipient.getAccountId(),
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR));

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I successfully schedule a crypto account create")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewCryptoAccountSchedule() throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 1;
        currentSignersCount = 0 + signatoryCountOffset;

        ExpandedAccountId alice = accountClient.getAccount(AccountClient.AccountNameEnum.ALICE);
        scheduledTransaction = accountClient
                .getAccountCreateTransaction(
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR),
                        KeyList.of(alice.getPublicKey()),
                        true,
                        "scheduled account create");

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I schedule a crypto transfer with {int} initial signatures but require an additional signature from " +
            "{string}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewCryptoAccountSchedule(int initSignatureCount, String accountName) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 2 + initSignatureCount; // new account, accountName and initSignatureCount
        currentSignersCount = initSignatureCount + signatoryCountOffset;
        ExpandedAccountId finalSignatory = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));

        KeyList privateKeyList = new KeyList();
        KeyList publicKeyList = new KeyList();
        for (int i = 0; i < initSignatureCount; i++) {
            PrivateKey accountKey = PrivateKey.generate();
            privateKeyList.add(accountKey);

            publicKeyList.add(accountKey.getPublicKey());
        }

        // additional signatory not provided up front to prevent schedule from executing
        publicKeyList.add(finalSignatory.getPublicKey());

        ExpandedAccountId newAccountId = accountClient
                .createCryptoAccount(
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR),
                        false,
                        publicKeyList,
                        "scheduled crypto transfer");

        scheduledTransaction = accountClient
                .getCryptoTransferTransaction(
                        newAccountId.getAccountId(),
                        accountClient.getSdkClient().getOperatorId(),
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR));

        // add sender private key to ensure only Alice's signature is the only signature left that is required
        privateKeyList.add(newAccountId.getPrivateKey());

        createNewSchedule(scheduledTransaction, privateKeyList);
        currentSignersCount++;
    }

    @Given("I successfully schedule a token transfer from {string} to {string}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewTokenTransferSchedule(String senderName, String receiverName) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 2;
        currentSignersCount = 0 + signatoryCountOffset;
        ExpandedAccountId sender = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(senderName));
        ExpandedAccountId receiver = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(receiverName));

        // create token
        PrivateKey tokenKey = PrivateKey.generate();
        PublicKey tokenPublicKey = tokenKey.getPublicKey();
        log.debug("Token creation PrivateKey : {}, PublicKey : {}", tokenKey, tokenPublicKey);

        networkTransactionResponse = tokenClient.createToken(
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                sender,
                DEFAULT_TINY_HBAR);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        TokenId tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);

        // associate new account, sender as treasury is auto associated
        log.debug("Associate receiver: {} with token: {}", receiverName, tokenId);
        networkTransactionResponse = tokenClient.asssociate(receiver, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());

        Hbar hbarAmount = Hbar.fromTinybars(DEFAULT_TINY_HBAR);
        scheduledTransaction = tokenClient
                .getTokenTransferTransaction(
                        tokenId,
                        sender.getAccountId(),
                        receiver.getAccountId(),
                        10)
                // add Hbar transfer logic
                .addHbarTransfer(receiver.getAccountId(), hbarAmount.negated())
                .addHbarTransfer(sender.getAccountId(), hbarAmount);

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I successfully schedule a topic message submit with {string}'s submit key")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewHCSSchedule(String accountName) throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 1;
        currentSignersCount = 0 + signatoryCountOffset;
        ExpandedAccountId submitAdmin = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));

        // create topic w submit key
        log.debug("Create new topic with {}'s submit key", accountName);
        networkTransactionResponse = topicClient
                .createTopic(accountClient.getTokenTreasuryAccount(), submitAdmin.getPublicKey());
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

    private void createNewSchedule(Transaction transaction, KeyList innerSignatureKeyList) throws PrecheckStatusException,
            ReceiptStatusException, TimeoutException {
        log.debug("Schedule creation ");

        // create signatures list
        networkTransactionResponse = scheduleClient.createSchedule(
                scheduleClient.getSdkClient().getExpandedOperatorAccountId(),
                transaction,
                "New Mirror Acceptance Schedule_" + Instant.now(),
                innerSignatureKeyList);
        assertNotNull(networkTransactionResponse.getTransactionId());

        // cache schedule create transaction id for confirmation of scheduled transaction later
        scheduleCreateTransactionId = networkTransactionResponse.getTransactionId();

        assertNotNull(networkTransactionResponse.getReceipt());
        scheduleId = networkTransactionResponse.getReceipt().scheduleId;
        assertNotNull(scheduleId);
    }

    public void signSignature(ExpandedAccountId signatoryAccount) throws PrecheckStatusException,
            ReceiptStatusException, TimeoutException {
        currentSignersCount++; // add signatoryAccount and payer
        networkTransactionResponse = scheduleClient.signSchedule(
                signatoryAccount,
                scheduleId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the scheduled transaction is signed by {string}")
    public void accountSignsSignature(String accountName) throws PrecheckStatusException, ReceiptStatusException,
            TimeoutException {
        log.debug("{} signs scheduledTransaction", accountName);
        signSignature(accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName)));
    }

    @Then("the scheduled transaction is signed by treasuryAccount")
    public void treasurySignsSignature() throws PrecheckStatusException, ReceiptStatusException,
            TimeoutException {
        log.debug("treasuryAccount signs scheduledTransaction");
        signSignature(accountClient.getTokenTreasuryAccount());
    }

    @When("I successfully delete the schedule")
    public void deleteSchedule() throws PrecheckStatusException, ReceiptStatusException, TimeoutException {
        networkTransactionResponse = scheduleClient.deleteSchedule(scheduleId);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the network confirms schedule presence")
    public void verifyNetworkScheduleResponse() throws TimeoutException, PrecheckStatusException {
        scheduleInfo = new ScheduleInfoQuery()
                .setScheduleId(scheduleId)
                .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                .execute(scheduleClient.getClient());

        log.trace("scheduleInfo: {}", scheduleInfo);
        validateScheduleInfo(scheduleInfo);
    }

    @Then("the network confirms the schedule is executed")
    public void verifyNetworkScheduleExecutedResponse() throws TimeoutException,
            PrecheckStatusException,
            ReceiptStatusException {
        verifyNetworkScheduleCompletion(false);
    }

    @Then("the network confirms the schedule is deleted")
    public void verifyNetworkScheduleDeletedResponse() throws TimeoutException,
            PrecheckStatusException,
            ReceiptStatusException {
        verifyNetworkScheduleCompletion(true);
    }

    @Then("the network confirms some signers have provided their signatures")
    public void verifyPartialScheduleFromNetwork() throws TimeoutException, PrecheckStatusException {
        verifyScheduleInfoFromNetwork(currentSignersCount);
    }

    @Then("the mirror node REST API should return status {int} for the schedule transaction")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPIResponses(int status) {
        log.info("Verify schedule transaction");
        String transactionId = networkTransactionResponse.getTransactionIdString();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorTransactionsResponse, status);

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
    }

    @Then("the mirror node REST API should verify the executed schedule entity")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyExecutedScheduleFromMirror() {
        MirrorScheduleResponse mirrorSchedule = verifyScheduleFromMirror();
        assertThat(mirrorSchedule.getExecutedTimestamp()).isNotNull();
        verifyScheduledTransaction(mirrorSchedule.getExecutedTimestamp());
    }

    @Then("the mirror node REST API should verify the non executed schedule entity")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyNonExecutedScheduleFromMirror() {
        MirrorScheduleResponse mirrorSchedule = verifyScheduleFromMirror();
        assertThat(mirrorSchedule.getExecutedTimestamp()).isNull();
    }

    @Then("the mirror node REST API should verify the deleted schedule entity")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class, WebClientResponseException.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyDeletedScheduleFromMirror() {
        MirrorScheduleResponse mirrorSchedule = verifyScheduleFromMirror();
        assertThat(mirrorSchedule.getExecutedTimestamp()).isNull();
    }

    private void validateScheduleInfo(ScheduleInfo scheduleInfo) {
        assertNotNull(scheduleInfo);
        assertThat(scheduleInfo.scheduleId).isEqualTo(scheduleId);
        assertThat(scheduleInfo.adminKey)
                .isEqualTo(accountClient.getSdkClient().getExpandedOperatorAccountId().getPublicKey());
        assertThat(scheduleInfo.payerAccountId)
                .isEqualTo(accountClient.getSdkClient().getExpandedOperatorAccountId().getAccountId());
    }

    private void verifyNetworkScheduleCompletion(boolean deleted) throws TimeoutException,
            PrecheckStatusException,
            ReceiptStatusException {
        scheduleInfo = new ScheduleInfoQuery()
                .setScheduleId(scheduleId)
                .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                .execute(scheduleClient.getClient());

        // verify executed from 3 min record, set scheduled=true on scheduleCreateTransactionId and get receipt
        validateScheduleInfo(scheduleInfo);

        if (deleted) {
            assertThat(scheduleInfo.deletedAt).isNotNull();
            assertThat(scheduleInfo.executedAt).isNull();
        } else {
            assertThat(scheduleInfo.deletedAt).isNull();
            assertThat(scheduleInfo.executedAt).isNotNull();
        }

        log.info("Schedule {} was confirmed to be executed by network state", scheduleId);

        TransactionId scheduledTransactionId = scheduleCreateTransactionId.setScheduled(true);
        assertNotNull(scheduledTransactionId);
        log.debug("Executed transaction {}.", scheduledTransactionId);

        TransactionReceipt transactionReceipt = scheduledTransactionId.getReceipt(scheduleClient.getClient());
        assertNotNull(transactionReceipt);
    }

    private void verifyScheduleInfoFromNetwork(int expectedSignatoriesCount) throws TimeoutException,
            PrecheckStatusException {
        scheduleInfo = new ScheduleInfoQuery()
                .setScheduleId(scheduleId)
                .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                .execute(scheduleClient.getClient());

        assertNotNull(scheduleInfo);
        assertThat(scheduleInfo.scheduleId).isEqualTo(scheduleId);
        assertThat(scheduleInfo.signatories).isNotEmpty();

        assertThat(scheduleInfo.signatories.size()).isEqualTo(expectedSignatoriesCount);
    }

    private MirrorScheduleResponse verifyScheduleFromMirror() {
        MirrorScheduleResponse mirrorSchedule = mirrorClient.getScheduleInfo(scheduleId.toString());

        assertNotNull(mirrorSchedule);
        assertThat(mirrorSchedule.getScheduleId()).isEqualTo(scheduleId.toString());
        log.info("{} has {} signatories", scheduleId, mirrorSchedule.getSignatures().size());

        Set signatureSet = new HashSet<String>();
        mirrorSchedule.getSignatures().forEach((k) -> {
            // get unique set of signatures
            signatureSet.add(k.getPublicKeyPrefix());
        });
        assertThat(signatureSet.size()).isEqualTo(currentSignersCount);

        return mirrorSchedule;
    }

    private void verifyScheduledTransaction(String timestamp) {
        log.info("Verify scheduled transaction {}", timestamp);
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactionInfoByTimestamp(timestamp);

        MirrorTransaction mirrorTransaction = verifyMirrorTransactionsResponse(mirrorTransactionsResponse, HttpStatus.OK
                .value());

        assertThat(mirrorTransaction.getConsensusTimestamp()).isEqualTo(timestamp);
        assertThat(mirrorTransaction.isScheduled()).isTrue();
    }

    private MirrorTransaction verifyMirrorTransactionsResponse(MirrorTransactionsResponse mirrorTransactionsResponse,
                                                               int status) {
        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        MirrorTransaction mirrorTransaction = transactions.get(0);

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();

        return mirrorTransaction;
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

    @After("@schedulebase")
    public void closeClients() {
        log.debug("Closing schedule feature clients");
        accountClient.close();
        mirrorClient.close();
        tokenClient.close();
        topicClient.close();
    }
}
