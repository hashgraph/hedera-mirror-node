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

import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.After;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import java.util.concurrent.TimeoutException;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;

@Log4j2
@Cucumber
@Data
public class AccountFeature {
    private long balance;
    private AccountId accountId;
    private long startingBalance;

    @Autowired
    private AccountClient accountClient;

    @When("I request balance info for this account")
    public void getAccountBalance() throws TimeoutException, PrecheckStatusException {
        balance = accountClient.getBalance();
    }

    @Then("the crypto balance should be greater than or equal to {long}")
    public void isGreaterOrEqualThan(long threshold) {
        assertTrue(balance >= threshold);
    }

    @When("I create a new account with balance {long} tℏ")
    public void createNewAccount(long initialBalance) throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        accountId = accountClient.createNewAccount(initialBalance).getAccountId();
        assertNotNull(accountId);
    }

    @When("I send {long} tℏ to account {int}")
    public void sendTinyHbars(long amount, int accountNum) throws TimeoutException, PrecheckStatusException,
            ReceiptStatusException {
        accountId = new AccountId(accountNum);
        startingBalance = accountClient.getBalance(accountId);
        TransactionReceipt receipt = accountClient.sendCryptoTransfer(accountId, Hbar.fromTinybars(amount));
        assertNotNull(receipt);
    }

    @When("I send {int} ℏ to account {int}")
    public void sendHbars(int amount, int accountNum) throws TimeoutException, PrecheckStatusException,
            ReceiptStatusException {
        accountId = new AccountId(accountNum);
        startingBalance = accountClient.getBalance(accountId);
        TransactionReceipt receipt = accountClient.sendCryptoTransfer(accountId, Hbar.from(amount));
        assertNotNull(receipt);
    }

    @Then("the new balance should reflect cryptotransfer of {long}")
    public void accountReceivedFunds(long amount) throws TimeoutException, PrecheckStatusException {
        assertTrue(accountClient.getBalance(accountId) >= startingBalance + amount);
    }

    @After
    public void closeClients() {
        try {
            accountClient.getSdkClient().close();
        } catch (Exception ex) {
            log.warn("Error closing SDK client : {}", ex);
        }
    }
}
