package com.hedera.mirror.monitor.subscribe;

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

import com.hedera.mirror.monitor.subscribe.grpc.GrpcSubscriberProperties;
import com.hedera.mirror.monitor.subscribe.rest.RestSubscriberProperties;

class SubscribePropertiesTest {

    private GrpcSubscriberProperties grpcSubscriberProperties;
    private RestSubscriberProperties restSubscriberProperties;
    private SubscribeProperties subscribeProperties;

    @BeforeEach
    void setup() {
        grpcSubscriberProperties = new GrpcSubscriberProperties();
        restSubscriberProperties = new RestSubscriberProperties();
        subscribeProperties = new SubscribeProperties();
        subscribeProperties.getGrpc().put("grpc1", grpcSubscriberProperties);
        subscribeProperties.getRest().put("rest1", restSubscriberProperties);
    }

    @Test
    void validate() {
        subscribeProperties.validate();
        assertThat(grpcSubscriberProperties.getName()).isEqualTo("grpc1");
        assertThat(restSubscriberProperties.getName()).isEqualTo("rest1");
    }

    @Test
    void duplicateName() {
        subscribeProperties.getGrpc().put("rest1", grpcSubscriberProperties);
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void emptyName(String name) {
        subscribeProperties.getGrpc().put(name, grpcSubscriberProperties);
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @Test
    void nullName() {
        subscribeProperties.getGrpc().put(null, grpcSubscriberProperties);
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @Test
    void noScenarios() {
        subscribeProperties.getGrpc().clear();
        subscribeProperties.getRest().clear();
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @Test
    void noScenariosDisabled() {
        subscribeProperties.setEnabled(false);
        subscribeProperties.getGrpc().clear();
        subscribeProperties.getRest().clear();
        subscribeProperties.validate();
    }
}
