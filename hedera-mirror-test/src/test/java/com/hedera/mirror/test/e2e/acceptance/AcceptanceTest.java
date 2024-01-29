/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance;

import com.hedera.mirror.test.e2e.acceptance.client.AccountClient.AccountNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.ContractClient.NodeNameEnum;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient.TokenNameEnum;
import io.cucumber.java.ParameterType;
import io.cucumber.spring.CucumberContextConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.boot.test.context.SpringBootTest;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@SpringBootTest(properties = "spring.main.banner-mode=off")
@CucumberContextConfiguration
@SuppressWarnings("java:S2187") // Ignore no tests in file warning
@Tag("acceptance")
public class AcceptanceTest {

    @ParameterType("\"?([A-Z]+)\"?")
    public AccountNameEnum account(String name) {
        return AccountNameEnum.valueOf(name);
    }

    @ParameterType("\"?([A-Z]+)\"?")
    public TokenNameEnum token(String name) {
        return TokenNameEnum.valueOf(name);
    }

    @ParameterType("\"?([A-Z]+)\"?")
    public NodeNameEnum node(String name) {
        return NodeNameEnum.valueOf(name);
    }
}
