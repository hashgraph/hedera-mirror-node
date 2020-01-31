package com.hedera.mirror.test.e2e.acceptance.steps;

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

import static org.junit.jupiter.api.Assertions.*;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.SDKClient;
import com.hedera.mirror.test.e2e.acceptance.config.ClientConnectionConfig;

@Log4j2
@Cucumber
public class AccountFeature {
    private AccountId accountId;
    private long balance;
    private SDKClient sdkClient;

    @Autowired
    private ClientConnectionConfig clientConnectionConfig;

    @Given("User obtained SDK client for account feature")
    public void getSDKClient() {
        assertNotNull(clientConnectionConfig, "clientConnectionConfig is null");

        if (sdkClient == null) {
            sdkClient = new SDKClient(clientConnectionConfig);
        }
    }

    @Given("I provided an account string of {string}")
    public void retrieveAccount(String targetAccount) {
        // if no account specified check operator balance
        if (targetAccount.isEmpty()) {
            accountId = sdkClient.getOperatorId();
        } else {
            accountId = AccountId.fromString(targetAccount);
        }
    }

    @When("I request balance info for this account")
    public void getAccountBalance() throws HederaStatusException {
        balance = AccountClient.getBalance(sdkClient.getClient(), accountId);
    }

    @Then("the result should be greater than or equal to {long}")
    public void isGreaterOrEqualThan(long threshold) {
        assertTrue(balance >= threshold);
    }

    @After
    public void closeClients() {

        if (sdkClient != null) {
            try {
                sdkClient.close();
            } catch (Exception ex) {
                log.warn("Error closing SDK client : {}", ex);
            }
        }
    }
}
