package com.hedera.mirror.monitor.generator;

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
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.monitor.publish.TransactionType;

@Data
@Validated
public class ScenarioProperties {

    @DurationMin(seconds = 30)
    private Duration duration = Duration.ofMinutes(10L);

    private boolean enabled = true;

    @Min(0)
    private long limit = 0;

    @NotBlank
    private String name;

    @NotEmpty
    private Map<String, Object> properties = new LinkedHashMap<>();

    private boolean receipt = false; // TODO: Retrieve a percentage of receipts and records

    private boolean record = false;

    @Min(0)
    private double tps = 10.0;

    @NotNull
    private TransactionType type;
}


