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
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public abstract class AbstractSubscriberProperties {

    @NotNull
    @DurationMin(seconds = 30)
    protected Duration duration = Duration.ofNanos(Long.MAX_VALUE);

    protected boolean enabled = true;

    @Min(0)
    protected long limit = 0; // 0 for unlimited

    @NotBlank
    protected String name;

    @NotNull
    protected RetryProperties retry = new RetryProperties();

    @Min(1)
    @Max(1024)
    protected int subscribers = 1;

    @Data
    @Validated
    public static class RetryProperties {

        @Min(0)
        private long maxAttempts = 16L;

        @NotNull
        @DurationMin(millis = 500L)
        private Duration maxBackoff = Duration.ofSeconds(8L);

        @NotNull
        @DurationMin(millis = 100L)
        private Duration minBackoff = Duration.ofMillis(250L);
    }
}


