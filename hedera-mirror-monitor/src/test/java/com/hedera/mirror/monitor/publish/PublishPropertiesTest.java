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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublishPropertiesTest {

    private PublishScenarioProperties publishScenarioProperties;
    private PublishProperties publishProperties;

    @BeforeEach
    void setup() {
        publishScenarioProperties = new PublishScenarioProperties();
        publishProperties = new PublishProperties();
        publishProperties.getScenarios().put("test1", publishScenarioProperties);
    }

    @Test
    void validate() {
        publishProperties.validate();
        assertThat(publishScenarioProperties.getName()).isEqualTo("test1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void emptyName(String name) {
        publishProperties.getScenarios().put(name, publishScenarioProperties);
        assertThrows(IllegalArgumentException.class, publishProperties::validate);
    }

    @Test
    void nullName() {
        publishProperties.getScenarios().put(null, publishScenarioProperties);
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
