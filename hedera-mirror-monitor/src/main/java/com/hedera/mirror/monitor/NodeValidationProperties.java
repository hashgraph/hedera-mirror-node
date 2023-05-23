/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class NodeValidationProperties {

    private boolean enabled = true;

    @DurationMin(seconds = 30)
    @NotNull
    private Duration frequency = Duration.ofDays(1L);

    @Min(1)
    private int maxAttempts = 8;

    @DurationMin(millis = 250)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration maxBackoff = Duration.ofSeconds(2);

    @Min(1)
    private int maxThreads = 25;

    @DurationMin(millis = 250)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration minBackoff = Duration.ofMillis(500);

    @DurationMin(millis = 100L)
    @NotNull
    private Duration retryBackoff = Duration.ofMinutes(2L);

    // requestTimeout should be longer than the total retry time controlled by maxAttempts and backoffs
    // the default would result in a max of 11.5s without considering any network delay
    @DurationMin(millis = 500)
    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(15L);

    private boolean retrieveAddressBook = true;
}
