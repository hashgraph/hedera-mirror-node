package com.hedera.mirror.grpc.listener;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.grpc.listener")
public class ListenerProperties {

    @NotNull
    private Duration bufferInitial = Duration.ofSeconds(5);

    @Min(1)
    private int bufferSize = 50_000;

    private boolean enabled = true;

    @Min(32)
    private int maxPageSize = 10000;

    @NotNull
    private Duration pollingFrequency = Duration.ofSeconds(1);

    @NotNull
    private ListenerType type = ListenerType.SHARED_POLL;

    public enum ListenerType {
        POLL,
        SHARED_POLL
    }
}
