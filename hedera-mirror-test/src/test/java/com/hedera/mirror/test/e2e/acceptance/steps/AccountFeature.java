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
import java.util.List;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransfer;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Cucumber
@Data
public class AccountFeature {
    private long balance;
    private AccountId accountId;
    private long startingBalance;
    private NetworkTransactionResponse networkTransactionResponse;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private MirrorNodeClient mirrorClient;

    @When("I request balance info for this account")
    public void getAccountBalance() {
        balance = accountClient.getBalance();
        log.info("{} account balance is {}",
                accountClient.getSdkClient().getExpandedOperatorAccountId().getAccountId(),
                balance);
    }

    @Then("the crypto balance should be greater than or equal to {long}")
    public void isGreaterOrEqualThan(long threshold) {
        assertTrue(balance >= threshold);
    }

    @When("I create a new account with balance {long} tℏ")
    public void createNewAccount(long initialBalance) {
        accountId = accountClient.createNewAccount(initialBalance).getAccountId();
        assertNotNull(accountId);
    }

    @Given("I send {long} tℏ to {string}")
    public void treasuryDisbursement(long amount, String accountName) {
        accountId = accountClient
                .getAccount(AccountClient.AccountNameEnum.valueOf(accountName)).getAccountId();

        startingBalance = accountClient.getBalance(accountId);

        networkTransactionResponse = accountClient
                .sendCryptoTransfer(accountId, Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I send {long} tℏ to newly created account")
    public void sendTinyHbars(long amount) {
        startingBalance = accountClient.getBalance(accountId);
        networkTransactionResponse = accountClient.sendCryptoTransfer(accountId, Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I send {long} tℏ to account {int}")
    public void sendTinyHbars(long amount, int accountNum) {
        accountId = new AccountId(accountNum);
        startingBalance = accountClient.getBalance(accountId);
        networkTransactionResponse = accountClient.sendCryptoTransfer(accountId, Hbar.fromTinybars(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @When("I send {int} ℏ to account {int}")
    public void sendHbars(int amount, int accountNum) {
        accountId = new AccountId(accountNum);
        startingBalance = accountClient.getBalance(accountId);
        networkTransactionResponse = accountClient.sendCryptoTransfer(accountId, Hbar.from(amount));
        assertNotNull(networkTransactionResponse.getTransactionId());
        assertNotNull(networkTransactionResponse.getReceipt());
    }

    @Then("the new balance should reflect cryptotransfer of {long}")
    public void accountReceivedFunds(long amount) {
        assertThat(accountClient.getBalance(accountId)).isGreaterThanOrEqualTo(startingBalance + amount);
    }

    @Then("the mirror node REST API should return status {int} for the crypto transfer transaction")
    public void verifyMirrorAPIResponses(int status) {
        log.info("Verify transaction");
        String transactionId = networkTransactionResponse.getTransactionIdStringNoCheckSum();

        MirrorTransactionsResponse mirrorTransactionsResponse = mirrorClient.getTransactions(transactionId);

        verifyCryptoTransfers(mirrorTransactionsResponse, status, transactionId);
    }

    private void verifyCryptoTransfers(MirrorTransactionsResponse mirrorTransactionsResponse, int status,
                                       String transactionId) {
        List<MirrorTransaction> transactions = mirrorTransactionsResponse.getTransactions();
        assertNotNull(transactions);
        assertThat(transactions).isNotEmpty();

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

        assertThat(transferSum).isEqualTo(0);
    }
}
