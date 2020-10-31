package com.hedera.mirror.monitor.generator;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.base.Suppliers;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.mirror.monitor.publish.PublishRequest;

public class ConfigurableTransactionGeneratorTest {

    private static final String TOPIC_ID = "0.0.1000";

    private ScenarioProperties properties;
    private Supplier<ConfigurableTransactionGenerator> generator;

    @BeforeEach
    void init() {
        properties = new ScenarioProperties();
        properties.setName("test");
        properties.setProperties(Map.of("topicId", TOPIC_ID));
        properties.setType(TransactionType.CONSENSUS_SUBMIT_MESSAGE);
        generator = Suppliers.memoize(() -> new ConfigurableTransactionGenerator(properties));
    }

    @Test
    void next() {
        properties.setReceipt(true);
        properties.setRecord(true);
        assertRequest(generator.get().next());
    }

    @Test
    void unknownField() {
        properties.setProperties(Map.of("foo", "bar", "topicId", TOPIC_ID));
        assertThatThrownBy(() -> generator.get().next())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized field");
    }

    @Test
    void reachedLimit() {
        properties.setLimit(1);
        assertRequest(generator.get().next());
        assertThatThrownBy(() -> generator.get().next())
                .isInstanceOf(ScenarioException.class)
                .hasMessageContaining("Reached publish limit");
    }

    @Test
    void reachedDuration() {
        properties.setDuration(Duration.ofSeconds(-5L));
        assertThatThrownBy(() -> generator.get().next())
                .isInstanceOf(ScenarioException.class)
                .hasMessageContaining("Reached publish duration");
    }

    @Test
    void missingRequiredField() {
        properties.setProperties(Collections.emptyMap());
        assertThatThrownBy(() -> generator.get().next()).isInstanceOf(TransactionSupplierException.class);
    }

    private void assertRequest(PublishRequest publishRequest) {
        assertThat(publishRequest).isNotNull()
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("receipt", properties.isReceipt())
                .hasFieldOrPropertyWithValue("record", properties.isRecord())
                .hasFieldOrPropertyWithValue("type", properties.getType())
                .hasFieldOrPropertyWithValue("transactionBuilder.topicId", ConsensusTopicId.fromString(TOPIC_ID));
    }
}
