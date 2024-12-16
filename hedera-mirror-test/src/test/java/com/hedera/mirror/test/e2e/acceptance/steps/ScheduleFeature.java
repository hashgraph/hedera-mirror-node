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

import static com.hedera.mirror.test.e2e.acceptance.steps.AbstractFeature.ContractResource.ESTIMATE_PRECOMPILE;
import static com.hedera.mirror.test.e2e.acceptance.steps.EstimatePrecompileFeature.ContractMethods.CRYPTO_TRANSFER_HBARS;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.accountAmount;
import static com.hedera.mirror.test.e2e.acceptance.util.TestUtil.asAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.esaulpaugh.headlong.abi.Tuple;
import com.google.protobuf.ByteString;
import com.hedera.hashgraph.sdk.ContractExecuteTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
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
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenResponse;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.springframework.http.HttpStatus;

@CustomLog
@RequiredArgsConstructor
public class ScheduleFeature extends AbstractFeature{

    private static final int DEFAULT_TINY_HBAR = 1_000;
    private static final int SIGNATORY_COUNT_OFFSET = 1; // Schedule includes payer account which may not be a required

    private final AccountClient accountClient;
    private final MirrorNodeClient mirrorClient;
    private final ScheduleClient scheduleClient;
    private final TokenClient tokenClient;
    private TokenResponse tokenResponse;

    private int currentSignersCount;
    private NetworkTransactionResponse networkTransactionResponse;
    private ScheduleId scheduleId;
    private TransactionId scheduledTransactionId;
    private Long plusSecondsToExpire;
    private Long recipientInitialBalance;
    private Long senderInitialBalance;
    private static DeployedContract deployedPrecompileContract;
    private String precompileContractAddress;
    private String scheduleTxConsensusTimestamp;

    @Given("I successfully schedule a HBAR transfer from treasury to {account} {string} expiration time and wait for expiry {string} - plus {int} seconds")
    public void createNewHBarTransferSchedule(AccountNameEnum accountName, String hasExpirationTime, String waitForExpiry, int secondsToExpire) {
        this.plusSecondsToExpire = (long) secondsToExpire;
        Instant expirationTime;
        switch (hasExpirationTime) {
            case "without" -> expirationTime = null;
            case "with" -> expirationTime = Instant.now().plusSeconds(plusSecondsToExpire);
            default -> throw new IllegalArgumentException("Invalid expiration time");
        }

        currentSignersCount = SIGNATORY_COUNT_OFFSET;
        var recipient =
                accountClient.getAccount(accountName);
        recipientInitialBalance = accountClient.getBalance(recipient);
        senderInitialBalance = accountClient.getBalance(accountClient.getTokenTreasuryAccount());
        System.out.println("recipient: " + recipient.getAccountId());
        System.out.println("treasury: " + accountClient.getTokenTreasuryAccount().getAccountId());
        System.out.println("payer:  " + scheduleClient.getSdkClient().getExpandedOperatorAccountId());
        var scheduledTransaction = accountClient.getCryptoTransferTransaction(
                accountClient.getTokenTreasuryAccount().getAccountId(),
                recipient.getAccountId(),
                Hbar.fromTinybars(DEFAULT_TINY_HBAR));

        createNewSchedule(scheduledTransaction, null, expirationTime, Boolean.parseBoolean(waitForExpiry));
    }
    @Given("I wait for the schedule to expire")
    public void waitForScheduleToExpire() throws InterruptedException {
//            Thread.sleep((plusSecondsToExpire) * 1000 );
        var txConsensusTimestamp = convertStringToInstant(this.scheduleTxConsensusTimestamp);
        var expectedExecutedTimestamp = txConsensusTimestamp.plusSeconds(plusSecondsToExpire+2);

        while (Instant.now().isBefore(expectedExecutedTimestamp)) {
            System.out.println("Waiting for " + Duration.between(Instant.now(), expectedExecutedTimestamp).getSeconds() + " seconds...");
            Thread.sleep(500);
        }
    }

    private Instant convertStringToInstant(String timestamp) {
        BigDecimal bdTimestamp = new BigDecimal(timestamp);
        BigDecimal[] parts = bdTimestamp.divideAndRemainder(BigDecimal.ONE);
        BigDecimal integerPart = parts[0];
        BigDecimal fractionalPart = parts[1];
        long epochSeconds = integerPart.longValueExact();
        long nanos = fractionalPart.movePointRight(9).longValueExact();
        return Instant.ofEpochSecond(epochSeconds, nanos);
    }
//        private void waitUntilAndExecute(Instant expectedTimestamp){
//
//            while (Instant.now().isBefore(expectedTimestamp)) {
//                System.out.println("Waiting for " + Duration.between(Instant.now(), expectedTimestamp).getSeconds() + " seconds...");
//                Thread.sleep(500);
//            }



//            long delayInMillis = expectedTimestamp.toEpochMilli() - Instant.now().toEpochMilli();
//            if (delayInMillis > 0) {
//                System.out.println("Waiting for " + delayInMillis + " milliseconds...");
//                TimeUnit.MILLISECONDS.sleep(delayInMillis);
//            }
//            else {
//                System.out.println("Target time has already passed. Executing immediately.");
//            }
//            } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            System.err.println("Thread was interrupted during the wait.");
//        }










        // Parse the target timestamp (format: "yyyy-MM-dd HH:mm:ss")
//        Instant expectedExecutedTiemstamp = Instant.ofEpochMilli(createdTimestamp).plusSeconds(plusSecondsToExpire);

        // Wait until the target timestamp is reached


            //Add dummy transaction
//        var newAccount = accountClient.createNewAccount(10L);
//        assertNotNull(newAccount);
//        assertNotNull(newAccount.getAccountId());
//        var recipient =
//                accountClient.getAccount(AccountNameEnum.ALICE);
//        this.tokenResponse = tokenClient.getToken(TokenNameEnum.FUNGIBLE_AIRDROP);
//        var tokenId = tokenResponse.tokenId();
//        var tokenNetworkTransactionResponse = tokenResponse.response();
//        var dummyNetworkTransactionResponse = tokenClient.associate(recipient, tokenId);
//        assertNotNull(dummyNetworkTransactionResponse.getTransactionId());
//        assertNotNull(dummyNetworkTransactionResponse.getReceipt());


    @Given("I verify the account balances after the schedule execution")
    public void verifyAccountBalances() throws InterruptedException {
        var recipient = accountClient.getAccount(AccountNameEnum.BOB);
        var recipientBalance = accountClient.getBalance(recipient);
        var senderBalance = accountClient.getBalance(accountClient.getTokenTreasuryAccount());
        assertThat(recipientBalance).isEqualTo(recipientInitialBalance + DEFAULT_TINY_HBAR);
        assertThat(senderBalance).isEqualTo(senderInitialBalance - DEFAULT_TINY_HBAR);
    }
    @Given("I successfully deploy precompile contract")
    public void createNewPrecompileTestContract() {
        deployedPrecompileContract = getContract(ESTIMATE_PRECOMPILE);
        precompileContractAddress = deployedPrecompileContract.contractId().toSolidityAddress();
    }

    @Given("I successfully schedule a smart contract call - HBAR transfer from treasury to {account} {string} expiration time and wait for expiry {string} - plus {int} seconds")
    public void smartContractCallCryptoTransfer(AccountNameEnum accountName, String hasExpirationTime, String waitForExpiry, int secondsToExpire) {
        Instant expirationTime;
        switch (hasExpirationTime) {
            case "without" -> expirationTime = null;
            case "with" -> expirationTime = Instant.now().plusSeconds(plusSecondsToExpire);
            default -> throw new IllegalArgumentException("Invalid expiration time");
        }
        currentSignersCount = SIGNATORY_COUNT_OFFSET;
        var recipient =
                accountClient.getAccount(AccountNameEnum.ALICE);

        var senderTransfer = accountAmount(accountClient.getTokenTreasuryAccount().getAccountId().toSolidityAddress(), -10L, false);
        var receiverTransfer = accountAmount(recipient.getAccountId().toSolidityAddress(), 10L, false);
        var EMPTY_TUPLE_ARRAY = new Tuple[] {};
        var args = Tuple.of((Object) new Tuple[] {senderTransfer, receiverTransfer});
        var data = encodeDataToByteArray(ESTIMATE_PRECOMPILE, CRYPTO_TRANSFER_HBARS, args, EMPTY_TUPLE_ARRAY);
//        ContractFunctionParameters parameters =
//                new ContractFunctionParameters().addUint256(BigInteger.valueOf(1));
        var contractExecuteTransaction = new ContractExecuteTransaction()
                .setContractId(deployedPrecompileContract.contractId())
                .setGas(3_000_000)
                .setFunctionParameters(ByteString.copyFrom(data))
                .setFunction(CRYPTO_TRANSFER_HBARS.getSelector());
             /*   .setPayableAmount(new Hbar(10));*/

//        var scheduledContractTransaction = contractClient.executeContract(
//                deployedPrecompileContract.contractId(),
//                contractClient
//                        .getSdkClient()
//                        .getAcceptanceTestProperties()
//                        .getFeatureProperties()
//                        .getMaxContractFunctionGas(),
//                "cryptoTransferExternal",
//                data,
//                Hbar.fromTinybars(DEFAULT_TINY_HBAR));
        createNewSchedule(contractExecuteTransaction, null, expirationTime, Boolean.parseBoolean(waitForExpiry));
    }

    private void createNewSchedule(Transaction<?> transaction, KeyList innerSignatureKeyList, Instant expirationTime, boolean waitForExpiry) {
        // create signatures list
        networkTransactionResponse = scheduleClient.createSchedule(
                scheduleClient.getSdkClient().getExpandedOperatorAccountId(), transaction, innerSignatureKeyList, expirationTime, waitForExpiry);
        System.out.println("tarnsaction ID " + networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getTransactionId());
        scheduleTxConsensusTimestamp = mirrorClient.getTransactions(networkTransactionResponse.getTransactionIdStringNoCheckSum()).getTransactions().getFirst().getConsensusTimestamp();
//        createdTimestamp = networkTransactionResponse.getReceipt().scheduledTransactionId.validStart;
   //     createdTimestamp = mirrorClient.getTransactions(networkTransactionResponse.getTransactionId().toString()).getTransactions().getFirst().getConsensusTimestamp();
        assertNotNull(networkTransactionResponse.getReceipt());
        scheduleId = networkTransactionResponse.getReceipt().scheduleId;
        System.out.println("scheduleId: " + scheduleId);
        assertNotNull(scheduleId);

        // cache schedule create transaction id for confirmation of scheduled transaction later
        scheduledTransactionId = networkTransactionResponse.getReceipt().scheduledTransactionId;
        assertNotNull(scheduledTransactionId);
    }


//    private void deleteSchedule(Transaction<?> transaction, KeyList innerSignatureKeyList, Instant expirationTime, boolean waitForExpiry) {
//        // create signatures list
//        networkTransactionResponse = scheduleClient.deleteSchedule(scheduleId);
//        System.out.println("tarnsaction ID " + networkTransactionResponse.getTransactionId());
//        assertNotNull(networkTransactionResponse.getTransactionId());
//
//        assertNotNull(networkTransactionResponse.getReceipt());
//        scheduleId = networkTransactionResponse.getReceipt().scheduleId;
//        System.out.println("scheduleId: " + scheduleId);
//        assertNotNull(scheduleId);
//
//        // cache schedule create transaction id for confirmation of scheduled transaction later
//        scheduledTransactionId = networkTransactionResponse.getReceipt().scheduledTransactionId;
//        assertNotNull(scheduledTransactionId);
//    }

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

    @Then("the mirror node REST API should verify the {string} schedule entity {string} expiration time and wait for expiry {string}")
    public void verifyTheScheduleFromMirror(String scheduleStatus, String hasExpirationTime, String waitForExpiry) {
        verifyScheduleFromMirror(ScheduleStatus.valueOf(scheduleStatus), hasExpirationTime, Boolean.parseBoolean(waitForExpiry));
    }



//    @Then("the mirror node REST API should verify the non executed schedule entity")
//    public void verifyNonExecutedScheduleFromMirror() {
//        verifyScheduleFromMirror(ScheduleStatus.NON_EXECUTED);
//    }
//
//    @Then("the mirror node REST API should verify the deleted schedule entity")
//    public void verifyDeletedScheduleFromMirror() {
//        verifyScheduleFromMirror(ScheduleStatus.DELETED);
//    }

    private void verifyScheduleFromMirror(ScheduleStatus scheduleStatus, String hasExpirationTime, boolean waitForExpiry) {
        var mirrorSchedule = mirrorClient.getScheduleInfo(scheduleId.toString());
        assertNotNull(mirrorSchedule);
        assertThat(mirrorSchedule.getScheduleId()).isEqualTo(scheduleId.toString());

        // get unique set of signatures
        var signatureSet = mirrorSchedule.getSignatures().stream()
                .map(ScheduleSignature::getPublicKeyPrefix)
                .map(Bytes::wrap)
                .collect(Collectors.toSet());
        assertThat(signatureSet).hasSize(currentSignersCount);

        switch (scheduleStatus) {
            case NON_EXECUTED -> {assertThat(mirrorSchedule.getExecutedTimestamp())
                    .isNull();
                assertThat(mirrorSchedule.getCreatorAccountId())
                        .isEqualTo(scheduleClient.getSdkClient().getExpandedOperatorAccountId().toString());
                switch (hasExpirationTime) {
                    case "without" -> assertThat(mirrorSchedule.getExpirationTime()).isNull();
                    case "with" -> assertThat(mirrorSchedule.getExpirationTime()).isNotNull();
                    default -> throw new IllegalArgumentException("Invalid expiration time");
                }
                if(waitForExpiry){
                    assertThat(mirrorSchedule.getWaitForExpiry()).isTrue();
                }else {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isFalse();
                }
            }
            case DELETED -> {
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNull();
                assertThat(mirrorSchedule.getDeleted()).isTrue();
                assertThat(mirrorSchedule.getCreatorAccountId())
                        .isEqualTo(scheduleClient.getSdkClient().getExpandedOperatorAccountId().toString());
                switch (hasExpirationTime) {
                    case "without" -> assertThat(mirrorSchedule.getExpirationTime()).isNull();
                    case "with" -> assertThat(mirrorSchedule.getExpirationTime()).isNotNull();
                    default -> throw new IllegalArgumentException("Invalid expiration time");
                }
                if(waitForExpiry){
                    assertThat(mirrorSchedule.getWaitForExpiry()).isTrue();
                }else {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isFalse();
                }
            }
            case EXECUTED -> {
                TransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactionInfoByTimestamp(mirrorSchedule.getExecutedTimestamp());
                assertThat(mirrorTransactionsResponse.getTransactions())
                        .hasSize(1)
                        .first()
                        .returns("SUCCESS", com.hedera.mirror.rest.model.Transaction::getResult);
                verifyScheduledTransaction(mirrorSchedule.getExecutedTimestamp());
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNotNull();
                assertThat(mirrorSchedule.getCreatorAccountId())
                        .isEqualTo(scheduleClient.getSdkClient().getExpandedOperatorAccountId().toString());
                switch (hasExpirationTime) {
                    case "without" -> assertThat(mirrorSchedule.getExpirationTime()).isNull();
                    case "with" -> assertThat(mirrorSchedule.getExpirationTime()).isNotNull();
                    default -> throw new IllegalArgumentException("Invalid expiration time");
                }
                if(waitForExpiry){
                    assertThat(mirrorSchedule.getWaitForExpiry()).isTrue();
                }else {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isFalse();
                }
            }
            case EXPIRED -> {
                TransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactionInfoByTimestamp(mirrorSchedule.getExecutedTimestamp());
                assertThat(mirrorTransactionsResponse.getTransactions())
                        .hasSize(1)
                        .first()
                        .returns("INVALID_SIGNATURE", com.hedera.mirror.rest.model.Transaction::getResult);
                assertThat(mirrorSchedule.getExecutedTimestamp()).isNotNull();
                assertThat(mirrorSchedule.getDeleted()).isFalse();
                assertThat(mirrorSchedule.getCreatorAccountId())
                        .isEqualTo(scheduleClient.getSdkClient().getExpandedOperatorAccountId().toString());
                switch (hasExpirationTime) {
                    case "without" -> assertThat(mirrorSchedule.getExpirationTime()).isNull();
                    case "with" -> assertThat(mirrorSchedule.getExpirationTime()).isNotNull();
                    default -> throw new IllegalArgumentException("Invalid expiration time");
                }
                if(waitForExpiry){
                    assertThat(mirrorSchedule.getWaitForExpiry()).isTrue();
                }else {
                    assertThat(mirrorSchedule.getWaitForExpiry()).isFalse();
                }
            }
            default -> {}
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
        TransactionDetail mirrorTransaction = transactions.get(0);

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
        com.hedera.mirror.rest.model.Transaction mirrorTransaction = transactions.get(0);

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
