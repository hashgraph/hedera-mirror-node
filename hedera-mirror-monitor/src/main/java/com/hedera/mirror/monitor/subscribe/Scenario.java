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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Duration;
import java.util.Map;

import com.hedera.mirror.monitor.ScenarioProperties;
import com.hedera.mirror.monitor.ScenarioProtocol;
import com.hedera.mirror.monitor.ScenarioStatus;
import com.hedera.mirror.monitor.converter.DurationToStringSerializer;
import com.hedera.mirror.monitor.converter.StringToDurationDeserializer;

@JsonSerialize(as = Scenario.class)
public interface Scenario<P extends ScenarioProperties, T> {

    long getCount();

    @JsonDeserialize(using = StringToDurationDeserializer.class)
    @JsonSerialize(using = DurationToStringSerializer.class)
    Duration getElapsed();

    Map<String, Integer> getErrors();

    int getId();

    default String getName() {
        return getProperties().getName();
    }

    @JsonIgnore
    P getProperties();

    ScenarioProtocol getProtocol();

    double getRate();

    ScenarioStatus getStatus();

    boolean isRunning();

    void onComplete();

    void onError(Throwable t);

    void onNext(T response);
}
