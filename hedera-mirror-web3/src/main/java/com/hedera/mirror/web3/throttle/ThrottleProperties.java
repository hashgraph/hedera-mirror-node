/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.throttle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Setter
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.throttle")
public class ThrottleProperties {

    @Getter
    @Min(1)
    private long requestsPerSecond = 500;

    @Getter
    @Min(21_000)
    @Max(1_000_000_000)
    private long gasPerSecond = 1_000_000_000L;

    @Getter
    @Min(1)
    private int gasUnit = 1;

    @Getter
    @Min(0)
    @Max(100)
    private float gasLimitRefundPercent = 100;
}
