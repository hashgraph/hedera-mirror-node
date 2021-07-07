package com.hedera.mirror.monitor.generator;

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
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

import com.hedera.datagenerator.sdk.supplier.TransactionType;

@Data
@Validated
public class ScenarioProperties {

    @NotNull
    @DurationMin(seconds = 30)
    private Duration duration = Duration.ofNanos(Long.MAX_VALUE);

    private boolean enabled = true;

    @Min(0)
    private long limit = 0;

    private boolean logResponse = false;

    @Min(1)
    private int maxAttempts = 1;

    private String name;

    @NotNull
    private Map<String, String> properties = new LinkedHashMap<>();

    @Min(0)
    @Max(1)
    private double receiptPercent = 0.0;

    @Min(0)
    @Max(1)
    private double recordPercent = 0.0;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration timeout = Duration.ofSeconds(13);

    @Min(0)
    private double tps = 1.0;

    @NotNull
    private TransactionType type;

    public long getLimit() {
        return limit > 0 ? limit : Long.MAX_VALUE;
    }
}


