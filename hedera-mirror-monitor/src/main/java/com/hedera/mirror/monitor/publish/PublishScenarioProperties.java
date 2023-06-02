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

package com.hedera.mirror.monitor.publish;

import com.hedera.mirror.monitor.ScenarioProperties;
import com.hedera.mirror.monitor.publish.transaction.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class PublishScenarioProperties extends ScenarioProperties {

    private boolean logResponse = false;

    // Maximum length of the transaction memo string
    @Min(13) // 13 is the length of the memo timestamp
    @Max(100)
    private int maxMemoLength = 100;

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

    public PublishScenarioProperties() {
        getRetry().setMaxAttempts(1L);
    }

    @Override
    public long getLimit() {
        return limit > 0 ? limit : Long.MAX_VALUE;
    }
}
