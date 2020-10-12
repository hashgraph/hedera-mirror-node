package com.hedera.mirror.grpc.listener;

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

import java.time.Duration;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.grpc.listener")
public class ListenerProperties {

    private boolean enabled = true;

    @Min(32)
    private int maxPageSize = 5000;

    @Min(8192)
    @Max(65536)
    private int maxBufferSize = 16384;

    @DurationMin(seconds = 2)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration bufferTimeout = Duration.ofSeconds(4);

    @DurationMin(millis = 50)
    @NotNull
    private Duration frequency = Duration.ofMillis(500L);

    @NotNull
    private ListenerType type = ListenerType.REDIS;

    public enum ListenerType {
        NOTIFY,
        POLL,
        REDIS,
        SHARED_POLL
    }
}
