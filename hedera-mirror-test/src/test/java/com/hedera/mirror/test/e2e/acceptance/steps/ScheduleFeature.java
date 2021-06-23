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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import junit.framework.AssertionFailedError;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleInfo;
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
    private AccountClient accountClient;
    @Autowired
    private MirrorNodeClient mirrorClient;
    @Autowired
    private TokenClient tokenClient;
    @Autowired
    private TopicClient topicClient;
    @Autowired
    private ScheduleClient scheduleClient;

    private int currentSignersCount;
    private int expectedSignersCount;
    private NetworkTransactionResponse networkTransactionResponse;
    private ScheduleId scheduleId;
    private ScheduleInfo scheduleInfo;
    private Transaction scheduledTransaction;
    private TransactionId scheduledTransactionId;
    private final int signatoryCountOffset = 1; // Schedule map includes payer account which may not be a required
    // signatory
    private TokenId tokenId;
    private ExpandedAccountId tokenTreasuryAccount;

    @Given("I successfully schedule a treasury HBAR disbursement to {string}")
    public void createNewHBarTransferSchedule(String accountName) {
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
    public void createNewCryptoAccountSchedule() {
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
    public void createNewCryptoAccountSchedule(int initSignatureCount, String accountName) {
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
                        accountClient.getSdkClient().getExpandedOperatorAccountId().getAccountId(),
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR));

        // add sender private key to ensure only Alice's signature is the only signature left that is required
        privateKeyList.add(newAccountId.getPrivateKey());

        createNewSchedule(scheduledTransaction, privateKeyList);
        currentSignersCount++;
    }

    @Given("I successfully schedule a token transfer from {string} to {string}")
    public void createNewTokenTransferSchedule(String senderName, String receiverName) {
        expectedSignersCount = 2;
        currentSignersCount = 0 + signatoryCountOffset;
        tokenTreasuryAccount = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(senderName));
        ExpandedAccountId receiver = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(receiverName));

        // create token
        PrivateKey tokenKey = PrivateKey.generate();
        PublicKey tokenPublicKey = tokenKey.getPublicKey();
        log.trace("Token creation PrivateKey : {}, PublicKey : {}", tokenKey, tokenPublicKey);

        networkTransactionResponse = tokenClient.createToken(
                tokenClient.getSdkClient().getExpandedOperatorAccountId(),
                RandomStringUtils.randomAlphabetic(4).toUpperCase(),
                TokenFreezeStatus.FreezeNotApplicable_VALUE,
                TokenKycStatus.KycNotApplicable_VALUE,
                tokenTreasuryAccount,
                DEFAULT_TINY_HBAR);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
        tokenId = networkTransactionResponse.getReceipt().tokenId;
        assertNotNull(tokenId);

        // associate new account, sender as treasury is auto associated
        log.debug("Associate receiver: {} with token: {}", receiverName, tokenId);
        networkTransactionResponse = tokenClient.associate(receiver, tokenId);
        assertNotNull(networkTransactionResponse.getTransactionId());

        Hbar hbarAmount = Hbar.fromTinybars(DEFAULT_TINY_HBAR);
        scheduledTransaction = tokenClient
                .getTokenTransferTransaction(
                        tokenId,
                        tokenTreasuryAccount.getAccountId(),
                        receiver.getAccountId(),
                        10)
                // add Hbar transfer logic
                .addHbarTransfer(receiver.getAccountId(), hbarAmount)
                .addHbarTransfer(tokenTreasuryAccount.getAccountId(), hbarAmount.negated());

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I successfully schedule a topic message submit with {string}'s submit key")
    public void createNewHCSSchedule(String accountName) {
        expectedSignersCount = 1;
        currentSignersCount = 0 + signatoryCountOffset;
        ExpandedAccountId submitAdmin = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));

        // create topic w submit key
        log.debug("Create new topic with {}'s submit key", accountName);
        networkTransactionResponse = topicClient
                .createTopic(scheduleClient.getSdkClient().getExpandedOperatorAccountId(), submitAdmin.getPublicKey());
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

    private void createNewSchedule(Transaction transaction, KeyList innerSignatureKeyList) {
        log.debug("Schedule creation ");

        // create signatures list
        networkTransactionResponse = scheduleClient.createSchedule(
                scheduleClient.getSdkClient().getExpandedOperatorAccountId(),
                transaction,
                "New Mirror Acceptance Schedule_" + Instant.now(),
                innerSignatureKeyList);
        assertNotNull(networkTransactionResponse.getTransactionId());

        assertNotNull(networkTransactionResponse.getReceipt());
        scheduleId = networkTransactionResponse.getReceipt().scheduleId;
        assertNotNull(scheduleId);

        // cache schedule create transaction id for confirmation of scheduled transaction later
        scheduledTransactionId = networkTransactionResponse.getReceipt().scheduledTransactionId;
        assertNotNull(scheduledTransactionId);
    }

    public void signSignature(ExpandedAccountId signatoryAccount) {
        currentSignersCount++; // add signatoryAccount and payer
        networkTransactionResponse = scheduleClient.signSchedule(
                signatoryAccount,
                scheduleId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        scheduledTransactionId = networkTransactionResponse.getReceipt().scheduledTransactionId;
        assertNotNull(scheduledTransactionId);
    }

    @Then("the scheduled transaction is signed by {string}")
    public void accountSignsSignature(String accountName) throws Exception {
        log.debug("{} signs scheduledTransaction", accountName);
        signSignature(accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName)));
    }

    @Then("the scheduled transaction is signed by treasuryAccount")
    public void treasurySignsSignature() throws Exception {
        log.debug("treasuryAccount signs scheduledTransaction");
        signSignature(accountClient.getTokenTreasuryAccount());
    }

    @When("I successfully delete the schedule")
    public void deleteSchedule() throws Exception {
        networkTransactionResponse = scheduleClient.deleteSchedule(scheduleId);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the network confirms schedule presence")
    public void verifyNetworkScheduleResponse() throws Exception {
        verifyNetworkScheduleStatus(ScheduleStatus.NON_EXECUTED);
    }

    @Then("the network confirms the schedule is executed")
    public void verifyNetworkScheduleExecutedResponse() throws Exception {
        verifyNetworkScheduleStatus(ScheduleStatus.EXECUTED);
    }

    @Then("the network confirms the schedule is deleted")
    public void verifyNetworkScheduleDeletedResponse() throws Exception {
        verifyNetworkScheduleStatus(ScheduleStatus.DELETED);
    }

    @Then("the network confirms some signers have provided their signatures")
    public void verifyPartialScheduleFromNetwork() throws Exception {
        verifyScheduleInfoFromNetwork(currentSignersCount);
    }

    @Then("the mirror node REST API should return status {int} for the schedule transaction")
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
    public void verifyExecutedScheduleFromMirror() {
        MirrorScheduleResponse mirrorSchedule = verifyScheduleFromMirror(ScheduleStatus.EXECUTED);
        verifyScheduledTransaction(mirrorSchedule.getExecutedTimestamp());
    }

    @Then("the mirror node REST API should verify the non executed schedule entity")
    public void verifyNonExecutedScheduleFromMirror() {
        verifyScheduleFromMirror(ScheduleStatus.NON_EXECUTED);
    }

    @Then("the mirror node REST API should verify the deleted schedule entity")
    public void verifyDeletedScheduleFromMirror() {
        verifyScheduleFromMirror(ScheduleStatus.DELETED);
    }

    @After
    public void cleanup() throws Exception {
        // dissociate all applicable accounts from token to reduce likelihood of max token association error
        if (tokenId != null) {
            // a nonzero balance will result in a TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES error
            // not possible to wipe a treasury account as it results in CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT error
            // as a result to dissociate first delete token
            tokenClient.delete(tokenClient.getSdkClient().getExpandedOperatorAccountId(), tokenId);
            dissociateAccount(tokenTreasuryAccount);
            dissociateAccount(tokenClient.getSdkClient().getExpandedOperatorAccountId());
            tokenId = null;
        }
    }

    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@acceptanceTestProperties.backOffPeriod.toMillis()}"),
            maxAttemptsExpression = "#{@acceptanceTestProperties.maxRetries}")
    public void verifyNetworkScheduleStatus(ScheduleStatus scheduleStatus) {
        scheduleInfo = scheduleClient.getScheduleInfo(scheduleId);

        // verify executed from 3 min record, set scheduled=true on scheduleCreateTransactionId and get receipt
        validateScheduleInfo(scheduleInfo);

        switch (scheduleStatus) {
            case NON_EXECUTED:
                assertThat(scheduleInfo.executedAt).isNull();
                assertThat(scheduleInfo.deletedAt).isNull();
                break;
            case EXECUTED:
                assertThat(scheduleInfo.deletedAt).isNull();
                assertThat(scheduleInfo.executedAt).isNotNull();
                TransactionReceipt transactionReceipt = scheduleClient.getTransactionReceipt(scheduledTransactionId);
                assertNotNull(transactionReceipt);
                log.debug("Executed transaction {} was confirmed", scheduledTransactionId);
                break;
            case DELETED:
                assertThat(scheduleInfo.deletedAt).isNotNull();
                assertThat(scheduleInfo.executedAt).isNull();
                break;
            default:
                break;
        }

        log.info("Schedule {} status was confirmed by network state", scheduleId);
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

    private void validateScheduleInfo(ScheduleInfo scheduleInfo) {
        assertNotNull(scheduleInfo);
        assertThat(scheduleInfo.scheduleId).isEqualTo(scheduleId);
        assertThat(scheduleInfo.adminKey)
                .isEqualTo(accountClient.getSdkClient().getExpandedOperatorAccountId().getPublicKey());
        assertThat(scheduleInfo.payerAccountId)
                .isEqualTo(accountClient.getSdkClient().getExpandedOperatorAccountId().getAccountId());
    }

    private void verifyScheduleInfoFromNetwork(int expectedSignatoriesCount) {
        scheduleInfo = scheduleClient.getScheduleInfo(scheduleId);

        assertNotNull(scheduleInfo);
        assertThat(scheduleInfo.scheduleId).isEqualTo(scheduleId);
        assertThat(scheduleInfo.signatories.size()).isEqualTo(expectedSignatoriesCount);
    }

    private MirrorScheduleResponse verifyScheduleFromMirror(ScheduleStatus scheduleStatus) {
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

        switch (scheduleStatus) {
            case DELETED:
            case NON_EXECUTED:
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNull();
                break;
            case EXECUTED:
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNotNull();
                break;
            default:
                break;
        }

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

    @RequiredArgsConstructor
    public enum ScheduleStatus {
        NON_EXECUTED,
        EXECUTED,
        DELETED
    }
}
