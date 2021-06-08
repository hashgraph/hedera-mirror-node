package com.hedera.mirror.monitor.publish;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.monitor.generator.ScenarioProperties;

class PublishPropertiesTest {

    private ScenarioProperties scenarioProperties;
    private PublishProperties publishProperties;

    @BeforeEach
    void setup() {
        scenarioProperties = new ScenarioProperties();
        scenarioProperties.setName("test1");

        publishProperties = new PublishProperties();
        publishProperties.getScenarios().add(scenarioProperties);
    }

    @Test
    void validate() {
        publishProperties.validate();
    }

    @Test
    void duplicateName() {
        ScenarioProperties scenarioProperties2 = new ScenarioProperties();
        scenarioProperties2.setName(scenarioProperties.getName());
        publishProperties.getScenarios().add(scenarioProperties2);
        assertThrows(IllegalArgumentException.class, publishProperties::validate);
    }

    @Test
    void noScenarios() {
        publishProperties.getScenarios().clear();
        assertThrows(IllegalArgumentException.class, publishProperties::validate);
    }

    @Test
    void noScenariosDisabled() {
        publishProperties.setEnabled(false);
        publishProperties.getScenarios().clear();
        publishProperties.validate();
    }
}
