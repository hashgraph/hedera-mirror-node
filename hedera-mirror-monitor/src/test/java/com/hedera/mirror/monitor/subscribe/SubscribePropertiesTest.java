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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.monitor.subscribe.grpc.GrpcSubscriberProperties;
import com.hedera.mirror.monitor.subscribe.rest.RestSubscriberProperties;

class SubscribePropertiesTest {

    private GrpcSubscriberProperties grpcSubscriberProperties;
    private RestSubscriberProperties restSubscriberProperties;
    private SubscribeProperties subscribeProperties;

    @BeforeEach
    void setup() {
        grpcSubscriberProperties = new GrpcSubscriberProperties();
        grpcSubscriberProperties.setName("grpc1");

        restSubscriberProperties = new RestSubscriberProperties();
        restSubscriberProperties.setName("rest1");

        subscribeProperties = new SubscribeProperties();
        subscribeProperties.getGrpc().add(grpcSubscriberProperties);
        subscribeProperties.getRest().add(restSubscriberProperties);
    }

    @Test
    void validate() {
        subscribeProperties.validate();
    }

    @Test
    void duplicateName() {
        restSubscriberProperties.setName(grpcSubscriberProperties.getName());
        assertThrows(IllegalArgumentException.class, subscribeProperties::validate);
    }

    @Test
    void duplicateRestName() {
        subscribeProperties.getRest().add(restSubscriberProperties);
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
