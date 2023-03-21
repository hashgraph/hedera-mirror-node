package com.hedera.mirror.monitor.publish;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

import com.hedera.mirror.monitor.AbstractScenario;
import com.hedera.mirror.monitor.ScenarioProtocol;

@SuppressWarnings("java:S2160")
public class PublishScenario extends AbstractScenario<PublishScenarioProperties, PublishResponse> {

    private final String memo;

    public PublishScenario(PublishScenarioProperties properties) {
        super(1, properties);
        String hostname = Objects.requireNonNullElse(System.getenv("HOSTNAME"), "unknown");
        this.memo = String.format("Monitor %s on %s", properties.getName(), hostname);
    }

    public String getMemo() {
        var memoMessage = System.currentTimeMillis() + " " + this.memo;
        return StringUtils.truncate(memoMessage, properties.getMaxMemoLength());
    }

    @Override
    public ScenarioProtocol getProtocol() {
        return ScenarioProtocol.GRPC;
    }

    @Override
    public void onError(Throwable throwable) {
        if (throwable instanceof PublishException publishException) {
            errors.add(publishException.getStatus());
        } else {
            super.onError(throwable);
        }
    }
}
