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
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.junit.platform.engine.Cucumber;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;

@Log4j2
@Cucumber
@SpringBootTest
public class AccountFeature {
    private long balance;

    @Autowired
    private AccountClient accountClient;

    @When("I request balance info for this account")
    public void getAccountBalance() throws HederaStatusException {
        balance = accountClient.getBalance();
    }

    @Then("the result should be greater than or equal to {long}")
    public void isGreaterOrEqualThan(long threshold) {
        assertTrue(balance >= threshold);
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
