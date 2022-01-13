package com.hedera.mirror.monitor;

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
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
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
    private Duration frequency = Duration.ofDays(365); // Effectively disable for now due to #2914

    @DurationMin(millis = 250)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration maxBackoff = Duration.ofSeconds(2);

    @Min(1)
    private int maxAttempts = 20;

    @DurationMin(millis = 250)
    @DurationMax(seconds = 10)
    @NotNull
    private Duration minBackoff = Duration.ofMillis(500);
}
