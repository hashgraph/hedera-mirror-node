package com.hedera.mirror.test.e2e.acceptance.steps;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import java.util.List;
import junit.framework.AssertionFailedError;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.CollectionUtils;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorCryptoAllowance;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransfer;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorCryptoAllowanceResponse;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;

@Log4j2
@Cucumber
@Data
public class AccountFeature extends AbstractFeature {
    private long balance;
    private AccountId ownerAccountId;
    private AccountId receiverAccountId;
    private ExpandedAccountId senderAccountId;
    private long startingBalance;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private MirrorNodeClient mirrorClient;

    @When("I request balance info for this account")
    public void getAccountBalance() {
        balance = accountClient.getBalance();
    }

    @Then("the crypto balance should be greater than or equal to {long}")
    public void isGreaterOrEqualThan(long threshold) {
        assertTrue(balance >= threshold);
    }

    @When("I create a new account with balance {long} tℏ")
    public void createNewAccount(long initialBalance) {
        senderAccountId = accountClient.createNewAccount(initialBalance);
        assertNotNull(senderAccountId);
        assertNotNull(senderAccountId.getAccountId());
    }

    @Given("I send {long} tℏ to {string}")
    public void treasuryDisbursement(long amount, String accountName) {
        senderAccountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(accountName));

        startingBalance = accountClient.getBalance(senderAccountId);

        networkTransactionResponse = accountClient
                .sendCryptoTransfer(senderAccountId.getAccountId(), Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I send {long} tℏ to newly created account")
    public void sendTinyHbars(long amount) {
        startingBalance = accountClient.getBalance(senderAccountId);
        networkTransactionResponse = accountClient
                .sendCryptoTransfer(senderAccountId.getAccountId(), Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I send {long} tℏ to account {int}")
    public void sendTinyHbars(long amount, int accountNum) {
        senderAccountId = new ExpandedAccountId(new AccountId(accountNum));
        startingBalance = accountClient.getBalance(senderAccountId);
        networkTransactionResponse = accountClient
                .sendCryptoTransfer(senderAccountId.getAccountId(), Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I send {int} ℏ to account {int}")
    public void sendHbars(int amount, int accountNum) {
        senderAccountId = new ExpandedAccountId(new AccountId(accountNum));
        startingBalance = accountClient.getBalance(senderAccountId);
        networkTransactionResponse = accountClient
                .sendCryptoTransfer(senderAccountId.getAccountId(), Hbar.from(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Given("I approve {string} to transfer up to {long} tℏ")
    public void approveCryptoAllowance(String accountName, long amount) {
        setCryptoAllowance(accountName, amount);
    }

    @Given("I adjust {string} transfer allowance to {long} tℏ")
    public void adjustCryptoAllowance(String accountName, long amount) {
        senderAccountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        setCryptoAllowance(senderAccountId.getAccountId(), -amount, false);
    }

    @When("{string} transfers {long} tℏ from their approved balance to {string}")
    public void transferFromAllowance(String senderAccountName, long amount, String receiverAccountName) {
        senderAccountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(senderAccountName));
        receiverAccountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(receiverAccountName)).getAccountId();
        networkTransactionResponse = accountClient.sendApprovedCryptoTransfer(
                senderAccountId,
                receiverAccountId,
                Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the new balance should reflect cryptotransfer of {long}")
    public void accountReceivedFunds(long amount) {
        assertThat(accountClient.getBalance(senderAccountId)).isGreaterThanOrEqualTo(startingBalance + amount);
    }

    @Then("the mirror node REST API should return status {int} for the crypto transfer transaction")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPICryptoTransferResponse(int status) {
        log.info("Verify transaction");
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();
        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        // verify valid set of transactions
        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();

        // verify transaction details
        MirrorTransaction mirrorTransaction = transactions.get(0);
        if (status == HttpStatus.OK.value()) {
            assertThat(mirrorTransaction.getResult()).isEqualTo("SUCCESS");
        }
        assertThat(mirrorTransaction.getTransactionId()).isEqualTo(transactionId);
        assertThat(mirrorTransaction.getValidStartTimestamp())
                .isEqualTo(networkTransactionResponse.getValidStartString());
        assertThat(mirrorTransaction.getName()).isEqualTo("CRYPTOTRANSFER");

        assertThat(mirrorTransaction.getTransfers().size()).isGreaterThanOrEqualTo(3); // network, node and transfer

        //verify transfer credit and debits balance out
        long transferSum = 0;
        for (MirrorTransfer cryptoTransfer : mirrorTransaction.getTransfers()) {
            transferSum += Long.valueOf(cryptoTransfer.getAmount());
        }

        assertThat(transferSum).isZero();
    }

    @Then("the mirror node REST API should confirm the approved {long} tℏ crypto transfer allowance")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyMirrorAPIApprovedCryptoTransferResponse(long approvedAmount) {
        verifyMirrorTransactionsResponse(mirrorClient, HttpStatus.OK.value());

        String ownerString = ownerAccountId.toString();
        String spenderString = senderAccountId.getAccountId().toString();
        MirrorCryptoAllowanceResponse mirrorCryptoAllowanceResponse =
                mirrorClient.getAccountCryptoAllowanceBySpender(ownerString, spenderString);

        // verify valid set of allowance
        assertThat(mirrorCryptoAllowanceResponse.getAllowances())
                .isNotEmpty()
                .first()
                .isNotNull()
                .returns(approvedAmount, MirrorCryptoAllowance::getAmountGranted)
                .returns(ownerString, MirrorCryptoAllowance::getOwner)
                .returns(spenderString, MirrorCryptoAllowance::getSpender)
                .extracting(MirrorCryptoAllowance::getTimestamp)
                .isNotNull()
                .satisfies(t -> assertThat(t.getFrom()).isNotBlank())
                .satisfies(t -> assertThat(t.getTo()).isBlank());
    }

    @When("I delete the crypto allowance for {string}")
    public void deleteCryptoAllowance(String spenderAccountName) {
        setCryptoAllowance(spenderAccountName, 0);
    }

    @Then("the mirror node REST API should confirm the crypto allowance deletion")
    @Retryable(value = {AssertionError.class, AssertionFailedError.class},
            backoff = @Backoff(delayExpression = "#{@restPollingProperties.minBackoff.toMillis()}"),
            maxAttemptsExpression = "#{@restPollingProperties.maxAttempts}")
    public void verifyCryptoAllowanceDelete() {
        log.info("Verify crypto allowance deletion transaction");
        verifyMirrorAPIApprovedCryptoTransferResponse(0);
    }

    @When("I remove all my allowances from my account")
    public void cleanUpAllowances() {
        MirrorCryptoAllowanceResponse mirrorCryptoAllowanceResponse =
                mirrorClient.getAccountCryptoAllowance(accountClient.getClient().getOperatorAccountId().toString());

        if (mirrorCryptoAllowanceResponse != null && !CollectionUtils
                .isEmpty(mirrorCryptoAllowanceResponse.getAllowances())) {
            mirrorCryptoAllowanceResponse.getAllowances().forEach(x -> {
                // set allowance to 0 for each non zero
                if (x.getAmountGranted() > 0) {
                    setCryptoAllowance(AccountId.fromString(x.getSpender()), 0, true);
                }
            });
        }
    }

    @Then("the mirror node REST API should confirm no granted allowances remain")
    public void verifyNoAllowances() {
        log.info("Verify crypto allowance deletion transaction");
        MirrorCryptoAllowanceResponse mirrorCryptoAllowanceResponse =
                mirrorClient.getAccountCryptoAllowance(accountClient.getClient().getOperatorAccountId().toString());
        var allowances = mirrorCryptoAllowanceResponse.getAllowances();
        assertThat(allowances).isNotEmpty();
        assertThat(allowances.stream().map(MirrorCryptoAllowance::getAmountGranted).reduce(0L, Long::sum)).isZero();
    }

    private void setCryptoAllowance(String accountName, long amount) {
        senderAccountId = accountClient.getAccount(AccountClient.AccountNameEnum.valueOf(accountName));
        setCryptoAllowance(senderAccountId.getAccountId(), amount, true);
    }

    private void setCryptoAllowance(AccountId accountId, long amount, boolean approve) {
        ownerAccountId = accountClient.getClient().getOperatorAccountId();
        networkTransactionResponse = approve ?
                accountClient.approveCryptoAllowance(accountId, Hbar.fromTinybars(amount)) :
                accountClient.adjustCryptoAllowance(accountId, Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }
}
