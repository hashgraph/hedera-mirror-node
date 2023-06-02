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

package com.hedera.mirror.monitor.subscribe.rest;

import com.hedera.mirror.monitor.subscribe.AbstractSubscriberProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class RestSubscriberProperties extends AbstractSubscriberProperties {

    @NotNull
    private Set<String> publishers = new LinkedHashSet<>();

    @Min(0)
    @Max(1)
    private double samplePercent = 1.0;

    @NotNull
    @DurationMin(millis = 500)
    private Duration timeout = Duration.ofSeconds(5);

    @Override
    public long getLimit() {
        return limit > 0 ? limit : Long.MAX_VALUE;
    }
}
