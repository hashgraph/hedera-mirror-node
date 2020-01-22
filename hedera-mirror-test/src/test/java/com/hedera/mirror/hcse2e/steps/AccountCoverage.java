package com.hedera.mirror.hcse2e.steps;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.log4j.Log4j2;
import org.junit.Assert;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.mirror.hcse2e.util.AccountHelper;
import com.hedera.mirror.hcse2e.util.SDKClient;

@Log4j2
public class AccountCoverage {
    private AccountId accountId;
    private long balance;

    @Given("I provided an account string of {string}")
    public void retrieveAccount(String targetAccount) {
        accountId = AccountId.fromString(targetAccount);
    }

    @When("I request balance info for this account")
    public void getAccountBalance() throws HederaStatusException {
        Client client = SDKClient.hederaClient();
        balance = AccountHelper.getBalance(client, accountId);
    }

    @Then("the result should be greater than or equal to {long}")
    public void isGreaterOrEqualThan(long threshold) {
        Assert.assertTrue(balance >= threshold);
    }
}
