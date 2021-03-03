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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomStringUtils;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.ScheduleInfo;
import com.hedera.hashgraph.sdk.ScheduleInfoQuery;
import com.hedera.hashgraph.sdk.ScheduleSignTransaction;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.hashgraph.sdk.TransferTransaction;
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

    @Given("I successfully schedule a treasury HBAR disbursement to {string}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewHBarTransferSchedule(String accountName) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 2;
        currentSignersCount = 0;
        ExpandedAccountId carol = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(accountName)); // receiverSigRequired
        scheduledTransaction = accountClient
                .getCryptoTransferTransaction(
                        accountClient.getTokenTreasuryAccount().getAccountId(),
                        carol.getAccountId(),
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR));

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I successfully schedule a crypto account create")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewCryptoAccountSchedule() throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 1;
        currentSignersCount = 0;

        ExpandedAccountId alice = accountClient.getAccount(AccountClient.AccountNameEnum.ALICE);
        scheduledTransaction = accountClient
                .getAccountCreateTransaction(
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR),
                        KeyList.of(alice.getPublicKey()),
                        false);

        createNewSchedule(scheduledTransaction, null);
    }

    @Given("I schedule a crypto transfer with {int} initial signatures but require an additional signature from " +
            "{string}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewCryptoAccountSchedule(int initSignatureCount, String accountName) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 2 + initSignatureCount; // new account, accountName and initSignatureCount
        currentSignersCount = initSignatureCount + 1;
        ExpandedAccountId finalSignatory = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));

        List<PrivateKey> privateKeyList = new ArrayList<>();
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
                        publicKeyList);

        scheduledTransaction = accountClient
                .getCryptoTransferTransaction(
                        newAccountId.getAccountId(),
                        accountClient.getSdkClient().getOperatorId(),
                        Hbar.fromTinybars(DEFAULT_TINY_HBAR));

        // add sender private key to ensure only Alice's signature is teh only signature left that is required
        privateKeyList.add(newAccountId.getPrivateKey());

        createNewSchedule(scheduledTransaction, privateKeyList);
    }

    @Given("I successfully schedule a token transfer from {string} to {string}")
    @Retryable(value = {PrecheckStatusException.class}, exceptionExpression = "#{message.contains('BUSY')}")
    public void createNewTokenTransferSchedule(String senderName, String receiverName) throws ReceiptStatusException,
            PrecheckStatusException,
            TimeoutException {
        expectedSignersCount = 2;
        currentSignersCount = 0;
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
        currentSignersCount = 0;
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

    private void createNewSchedule(Transaction transaction, List<PrivateKey> innerSignatureKeyList) throws PrecheckStatusException,
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
        currentSignersCount++;
        networkTransactionResponse = scheduleClient.signSchedule(
                signatoryAccount,
                scheduledTransaction,
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

        assertNotNull(scheduleInfo);
        assertThat(scheduleInfo.scheduleId).isEqualTo(scheduleId);
        assertThat(scheduleInfo.adminKey)
                .isEqualTo(accountClient.getSdkClient().getExpandedOperatorAccountId().getPublicKey());
        assertThat(scheduleInfo.payerAccountId)
                .isEqualTo(accountClient.getSdkClient().getExpandedOperatorAccountId().getAccountId());
    }

    @Then("the network confirms the executed schedule is removed from state")
    public void verifyNetworkScheduleNotPresentResponse() throws TimeoutException, PrecheckStatusException,
            ReceiptStatusException {
        boolean invalidSchedule = false;
        try {
            scheduleInfo = new ScheduleInfoQuery()
                    .setScheduleId(scheduleId)
                    .setNodeAccountIds(Collections.singletonList(AccountId.fromString(acceptanceProps.getNodeId())))
                    .execute(scheduleClient.getClient());

            // verify executed from 3 min record, set scheduled=true on scheduleCreateTransactionId and get receipt
        } catch (PrecheckStatusException ex) {
            assertThat(ex).hasMessageContaining("INVALID_SCHEDULE_ID");
            invalidSchedule = true;
        }

        assertThat(invalidSchedule).isTrue();
        log.info("Schedule {} no longer returned from network state", scheduleId);

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

    @Then("the network confirms some signers have provided their signatures")
    public void verifyPartialScheduleFromNetwork() throws TimeoutException, PrecheckStatusException {
        verifyScheduleInfoFromNetwork(currentSignersCount);
    }

    @Then("the mirror node REST API should return status {int} for the schedule transaction")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
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
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyExecutedScheduleFromMirror() {
        MirrorScheduleResponse mirrorSchedule = verifyScheduleFromMirror();
        assertThat(mirrorSchedule.getExecutedTimestamp()).isNotNull();
        verifyScheduledTransaction(mirrorSchedule.getExecutedTimestamp());
    }

    @Then("the mirror node REST API should verify the non executed schedule entity")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.delay.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyNonExecutedScheduleFromMirror() {
        MirrorScheduleResponse mirrorSchedule = verifyScheduleFromMirror();
        assertThat(mirrorSchedule.getExecutedTimestamp()).isNull();
    }

    public MirrorScheduleResponse verifyScheduleFromMirror() {
        MirrorScheduleResponse mirrorSchedule = mirrorClient.getScheduleInfo(scheduleId.toString());
        assertNotNull(mirrorSchedule);
        assertThat(mirrorSchedule.getScheduleId()).isEqualTo(scheduleId.toString());
        assertThat(mirrorSchedule.getSignatures().size()).isEqualTo(currentSignersCount);

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

    @When("I run the E2EMultiSigOnScheduleCreateAndScheduleSign")
    public void E2ESigOnScheduleCreateAndScheduleSign() throws Exception {
        Client client = scheduleClient.getClient();
        // User A is payer i.e. sdk operator
        AccountId operatorId = scheduleClient.getSdkClient().getExpandedOperatorAccountId().getAccountId();
        PublicKey payerAccountPublicKey = scheduleClient.getSdkClient().getExpandedOperatorAccountId().getPublicKey();
        List<AccountId> nodeAccountIds = Collections.singletonList(AccountId.fromString("0.0.3"));

        // Generate 3 random keys
        PrivateKey key1 = PrivateKey.generate();
        PrivateKey key2 = PrivateKey.generate();
        PrivateKey key3 = PrivateKey.generate();

        // Create a keylist from those keys. This key will be used as the new account's key
        // The reason we want to use a `KeyList` is to simulate a multi-party system where
        // multiple keys are required to sign.
        KeyList keyList = new KeyList();

        keyList.add(key1.getPublicKey());
        keyList.add(key2.getPublicKey());
        keyList.add(key3.getPublicKey());

        log.info("key1 private = " + key1);
        log.info("key1 public = " + key1.getPublicKey());
        log.info("key1 private = " + key2);
        log.info("key2 public = " + key2.getPublicKey());
        log.info("key3 private = " + key3);
        log.info("key3 public = " + key3.getPublicKey());
        log.info("keyList = " + keyList);

        // Creat the account with the `KeyList`
        TransactionResponse response = new AccountCreateTransaction()
                .setNodeAccountIds(Collections.singletonList(new AccountId(3)))
                // The only _required_ property here is `key`
                .setKey(keyList)
                .setInitialBalance(new Hbar(10))
                .execute(client);

        // This will wait for the receipt to become available
        TransactionReceipt receipt = response.getReceipt(client);

        AccountId accountId = Objects.requireNonNull(receipt.accountId);

        log.info("accountId = " + accountId);

        // Generate a `TransactionId`. This id is used to query the inner scheduled transaction
        // after we expect it to have been executed
        TransactionId transactionId = TransactionId.generate(operatorId);

        log.info("transactionId for scheduled transaction = " + transactionId);

        // Create a transfer transaction with 2/3 signatures.
        TransferTransaction transfer = new TransferTransaction()
                .setNodeAccountIds(Collections.singletonList(response.nodeId))
                .setTransactionId(transactionId)
                .addHbarTransfer(accountId, new Hbar(1).negated())
                .addHbarTransfer(operatorId, new Hbar(1))
                .freezeWith(client)
                .sign(key1);

        // Schedule the transaction
        ScheduleCreateTransaction scheduled = transfer.schedule();

        byte[] key2Signature = key2.signTransaction(transfer);

        scheduled.addScheduleSignature(key2.getPublicKey(), key2Signature);

        if (scheduled.getScheduleSignatures().size() != 2) {
            throw new Exception("Scheduled transaction has incorrect number of signatures: " + scheduled
                    .getScheduleSignatures().size());
        }

        receipt = scheduled.execute(client).getReceipt(client);

        // Get the schedule ID from the receipt
        ScheduleId scheduleId = Objects.requireNonNull(receipt.scheduleId);

        log.info("scheduleId = " + scheduleId);

        // Get the schedule info to see if `signatories` is populated with 2/3 signatures
        ScheduleInfo info = new ScheduleInfoQuery()
                .setNodeAccountIds(Collections.singletonList(response.nodeId))
                .setScheduleId(scheduleId)
                .execute(client);

        log.info("Schedule Info = " + info);

        transfer = (TransferTransaction) info.getTransaction();

        Map<AccountId, Hbar> transfers = transfer.getHbarTransfers();

        // Make sure the transfer transaction is what we expect
        if (transfers.size() != 2) {
            throw new Exception("more transfers than expected");
        }

        if (!transfers.get(accountId).equals(new Hbar(1).negated())) {
            throw new Exception("transfer for " + accountId + " is not what is expected " + transfers.get(accountId));
        }

        if (!transfers.get(operatorId).equals(new Hbar(1))) {
            throw new Exception("transfer for " + operatorId + " is not what is expected " + transfers.get(operatorId));
        }

        // Get the last signature for the inner scheduled transaction
        byte[] key3Signature = key3.signTransaction(transfer);

        log.info("sending schedule sign transaction");

        // Finally send this last signature to Hedera. This last signature _should_ mean the transaction executes
        // since all 3 signatures have been provided.
        ScheduleSignTransaction signTransaction = new ScheduleSignTransaction()
                .setNodeAccountIds(Collections.singletonList(response.nodeId))
                .setScheduleId(scheduleId)
                .addScheduleSignature(key3.getPublicKey(), key3Signature);

        if (signTransaction.getScheduleSignatures().size() != 1) {
            throw new Exception("Scheduled sign transaction has incorrect number of signatures: " + signTransaction
                    .getScheduleSignatures().size());
        }

        signTransaction.execute(client).getReceipt(client);

        // Query the schedule info again
        try {
            new ScheduleInfoQuery()
                    .setNodeAccountIds(Collections.singletonList(response.nodeId))
                    .setScheduleId(scheduleId)
                    .execute(client);
        } catch (PrecheckStatusException e) {
            log.info("Received " + e.status + " status code which implies scheduled transaction was executed");
        }
    }
}
