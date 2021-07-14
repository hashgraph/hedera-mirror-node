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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class TestSubscription implements Subscription {

    private long count = 1;
    private Duration elapsed = Duration.ofSeconds(1L);
    private Map<String, Integer> errors = new HashMap<>();
    private int id = 1;
    private String name = "Test";
    private AbstractSubscriberProperties properties;
    private SubscriberProtocol protocol = SubscriberProtocol.GRPC;
    private double rate = 1.0;
    private SubscriptionStatus status = SubscriptionStatus.RUNNING;

    @Override
    public String toString() {
        return name;
    }
}
