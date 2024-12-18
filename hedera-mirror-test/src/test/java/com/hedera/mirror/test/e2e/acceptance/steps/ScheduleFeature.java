/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.test.e2e.acceptance.steps;

import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.convertTimestamp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.ScheduleId;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.mirror.rest.model.ScheduleSignature;
import com.hedera.mirror.rest.model.TransactionByIdResponse;
import com.hedera.mirror.rest.model.TransactionDetail;
import com.hedera.mirror.rest.model.TransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.ScheduleClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.HttpStatus;

@CustomLog
@RequiredArgsConstructor
public class ScheduleFeature extends AbstractFeature {

    private static final int DEFAULT_TINY_HBAR = 1_000;
    private static final int SIGNATORY_COUNT_OFFSET = 1; // Schedule includes payer account which may not be a required

    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private final ScheduleClient scheduleClient;

    private int currentSignersCount;
    private NetworkTransactionResponse networkTransactionResponse;
    private ScheduleId scheduleId;
    private TransactionId scheduledTransactionId;
    private Duration plusSecondsToExpire;
    private String scheduleTxConsensusTimestamp;

    @Given(
            "I successfully schedule a HBAR transfer from treasury to {account} with expiration time {string} and wait for expiry {string}")
    public void TestcreateNewHBarTransferSchedule(
            AccountNameEnum accountName, String expirationTimeInSeconds, String waitForExpiry) {
        Instant expirationTime;
        if (expirationTimeInSeconds.equals("null")) {
            expirationTime = null;
        } else {
            this.plusSecondsToExpire = DurationStyle.detectAndParse(expirationTimeInSeconds);
            expirationTime = Instant.now().plus(plusSecondsToExpire);
        }

        currentSignersCount = SIGNATORY_COUNT_OFFSET;
        var recipient = accountClient.getAccount(accountName);
        var scheduledTransaction = accountClient.getCryptoTransferTransaction(
                accountClient.getTokenTreasuryAccount().getAccountId(),
                recipient.getAccountId(),
                Hbar.fromTinybars(DEFAULT_TINY_HBAR));

        createNewSchedule(scheduledTransaction, expirationTime, Boolean.parseBoolean(waitForExpiry));
    }

    @Given("I wait for the schedule to expire")
    public void waitForScheduleToExpire() {
        var txConsensusTimestamp = convertTimestamp(this.scheduleTxConsensusTimestamp);
        var expectedExecutedTimestamp = txConsensusTimestamp.plus(plusSecondsToExpire);

        await().atMost(Duration.ofSeconds(30))
                .pollDelay(Duration.ofMillis(100))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(Instant.now()).isAfterOrEqualTo(expectedExecutedTimestamp));

        // We need this dummy transaction in order to execute the schedule
        try {
            accountClient.executeTransaction(new AccountCreateTransaction(), null);
        } catch (Exception e) {
            log.info("Dummy transaction fails but triggers the schedule execution");
        }
    }

    private void createNewSchedule(Transaction<?> transaction, Instant expirationTime, boolean waitForExpiry) {
        // create signatures list
        networkTransactionResponse = scheduleClient.createSchedule(
                scheduleClient.getSdkClient().getExpandedOperatorAccountId(),
                transaction,
                null,
                expirationTime,
                waitForExpiry);
        assertNotNull(networkTransactionResponse.getTransactionId());
        scheduleTxConsensusTimestamp = Objects.requireNonNull(mirrorClient
                        .getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum())
                        .getTransactions())
                .getFirst()
                .getConsensusTimestamp();
        assertNotNull(networkTransactionResponse.getReceipt());
        scheduleId = networkTransactionResponse.getReceipt().scheduleId;
        assertNotNull(scheduleId);

        // cache schedule create transaction id for confirmation of scheduled transaction later
        scheduledTransactionId = networkTransactionResponse.getReceipt().scheduledTransactionId;
        assertNotNull(scheduledTransactionId);
    }

    public void signSignature(ExpandedAccountId signatoryAccount) {
        currentSignersCount++; // add signatoryAccount and payer
        networkTransactionResponse = scheduleClient.signSchedule(signatoryAccount, scheduleId);
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());

        scheduledTransactionId = networkTransactionResponse.getReceipt().scheduledTransactionId;
        assertNotNull(scheduledTransactionId);
    }

    @Then("the scheduled transaction is signed by {account}")
    public void accountSignsSignature(AccountNameEnum accountName) {
        signSignature(accountClient.getAccount(accountName));
    }

    @Then("the scheduled transaction is signed by treasuryAccount")
    public void treasurySignsSignature() {
        signSignature(accountClient.getTokenTreasuryAccount());
    }

    @When("I successfully delete the schedule")
    public void deleteSchedule() {
        networkTransactionResponse = scheduleClient.deleteSchedule(scheduleId);

        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the mirror node REST API should return status {int} for the schedule transaction")
    public void verifyMirrorAPIResponses(int status) {
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        TransactionByIdResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        TransactionDetail mirrorTransaction =
                verifyMirrorTransactionsResponse(mirrorTransactionsResponse, status, true);

        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
    }

    @RetryAsserts
    @Then(
            "the mirror node REST API should verify the {string} schedule entity {string} expiration time and wait for expiry {string}")
    public void verifyTheScheduleFromMirror(String scheduleStatus, String hasExpirationTime, String waitForExpiry) {
        verifyScheduleFromMirror(
                ScheduleStatus.valueOf(scheduleStatus), hasExpirationTime, Boolean.parseBoolean(waitForExpiry));
    }

    private void verifyScheduleFromMirror(
            ScheduleStatus scheduleStatus, String hasExpirationTime, boolean waitForExpiry) {
        var mirrorSchedule = mirrorClient.getScheduleInfo(scheduleId.toString());
        assertNotNull(mirrorSchedule);
        assertThat(mirrorSchedule.getScheduleId()).isEqualTo(scheduleId.toString());

        // get unique set of signatures
        var signatureSet = mirrorSchedule.getSignatures().stream()
                .map(ScheduleSignature::getPublicKeyPrefix)
                .filter(Objects::nonNull)
                .map(Bytes::wrap)
                .collect(Collectors.toSet());
        assertThat(signatureSet).hasSize(currentSignersCount);

        switch (scheduleStatus) {
            case NON_EXECUTED -> {
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNull();
                assertThat(mirrorSchedule.getCreatorAccountId())
                        .isEqualTo(scheduleClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .toString());
                switch (hasExpirationTime) {
                    case "without" -> assertThat(mirrorSchedule.getExpirationTime())
                            .isNull();
                    case "with" -> assertThat(mirrorSchedule.getExpirationTime())
                            .isNotNull();
                    default -> throw new IllegalArgumentException("Invalid expiration time");
                }
                if (waitForExpiry) {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isTrue();
                } else {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isFalse();
                }
            }
            case DELETED -> {
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNull();
                assertThat(mirrorSchedule.getDeleted()).isTrue();
                assertThat(mirrorSchedule.getCreatorAccountId())
                        .isEqualTo(scheduleClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .toString());
                switch (hasExpirationTime) {
                    case "without" -> assertThat(mirrorSchedule.getExpirationTime())
                            .isNull();
                    case "with" -> assertThat(mirrorSchedule.getExpirationTime())
                            .isNotNull();
                    default -> throw new IllegalArgumentException("Invalid expiration time");
                }
                if (waitForExpiry) {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isTrue();
                } else {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isFalse();
                }
            }
            case EXECUTED -> {
                TransactionsResponse mirrorTransactionsResponse =
                        mirrorClient.getTransactionInfoByTimestamp(mirrorSchedule.getExecutedTimestamp());
                assertThat(mirrorTransactionsResponse.getTransactions())
                        .hasSize(1)
                        .first()
                        .returns("SUCCESS", com.hedera.mirror.rest.model.Transaction::getResult);
                verifyScheduledTransaction(mirrorSchedule.getExecutedTimestamp());
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNotNull();
                assertThat(mirrorSchedule.getCreatorAccountId())
                        .isEqualTo(scheduleClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .toString());
                switch (hasExpirationTime) {
                    case "without" -> assertThat(mirrorSchedule.getExpirationTime())
                            .isNull();
                    case "with" -> assertThat(mirrorSchedule.getExpirationTime())
                            .isNotNull();
                    default -> throw new IllegalArgumentException("Invalid expiration time");
                }
                if (waitForExpiry) {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isTrue();
                } else {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isFalse();
                }
            }
            case EXPIRED -> {
                TransactionsResponse mirrorTransactionsResponse =
                        mirrorClient.getTransactionInfoByTimestamp(mirrorSchedule.getExecutedTimestamp());
                assertThat(mirrorTransactionsResponse.getTransactions())
                        .hasSize(1)
                        .first()
                        .returns("INVALID_SIGNATURE", com.hedera.mirror.rest.model.Transaction::getResult);
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNotNull();
                assertThat(mirrorSchedule.getDeleted()).isFalse();
                assertThat(mirrorSchedule.getCreatorAccountId())
                        .isEqualTo(scheduleClient
                                .getSdkClient()
                                .getExpandedOperatorAccountId()
                                .toString());
                switch (hasExpirationTime) {
                    case "without" -> assertThat(mirrorSchedule.getExpirationTime())
                            .isNull();
                    case "with" -> assertThat(mirrorSchedule.getExpirationTime())
                            .isNotNull();
                    default -> throw new IllegalArgumentException("Invalid expiration time");
                }
                if (waitForExpiry) {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isTrue();
                } else {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isFalse();
                }
            }
            default -> throw new IllegalArgumentException("Invalid schedule status");
        }
    }

    private void verifyScheduledTransaction(String timestamp) {
        TransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactionInfoByTimestamp(timestamp);

        com.hedera.mirror.rest.model.Transaction mirrorTransaction =
                verifyMirrorTransactionsResponse(mirrorTransactionsResponse, HttpStatus.OK.value(), false);

        assertThat(mirrorTransaction.getConsensusTimestamp()).isEqualTo(timestamp);
        assertThat(mirrorTransaction.getScheduled()).isTrue();
    }

    private TransactionDetail verifyMirrorTransactionsResponse(
            TransactionByIdResponse mirrorTransactionsResponse, int status, boolean verifyEntityId) {
        List<TransactionDetail> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        TransactionDetail mirrorTransaction = transactions.getFirst();

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();

        if (verifyEntityId) {
            assertThat(mirrorTransaction.getEntityId()).isEqualTo(scheduleId.toString());
        }

        return mirrorTransaction;
    }

    private com.hedera.mirror.rest.model.Transaction verifyMirrorTransactionsResponse(
            TransactionsResponse mirrorTransactionsResponse, int status, boolean verifyEntityId) {
        List<com.hedera.mirror.rest.model.Transaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();
        com.hedera.mirror.rest.model.Transaction mirrorTransaction = transactions.getFirst();

        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }

        assertThat(mirrorTransaction.getValidStartTimestamp()).isNotNull();
        assertThat(mirrorTransaction.getName()).isNotNull();
        assertThat(mirrorTransaction.getResult()).isNotNull();
        assertThat(mirrorTransaction.getConsensusTimestamp()).isNotNull();

        if (verifyEntityId) {
            assertThat(mirrorTransaction.getEntityId()).isEqualTo(scheduleId.toString());
        }

        return mirrorTransaction;
    }

    @RequiredArgsConstructor
    public enum ScheduleStatus {
        NON_EXECUTED,
        EXECUTED,
        DELETED,
        EXPIRED
    }
}
