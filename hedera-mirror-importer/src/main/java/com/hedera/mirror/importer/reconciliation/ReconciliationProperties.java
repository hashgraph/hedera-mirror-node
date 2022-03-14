package com.hedera.mirror.importer.reconciliation;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import java.time.Instant;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.importer.util.Utility;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.reconciliation")
class ReconciliationProperties {

    @NotBlank
    private String cron = "0 0 0 * * *"; // Every day at midnight

    private boolean enabled = true;

    @NotNull
    private Instant endDate = Utility.MAX_INSTANT_LONG;

    @NotNull
    private Instant startDate = Instant.EPOCH;

    private boolean token = false;

    public void setStartDate(Instant startDate) {
        if (startDate == null || startDate.isAfter(endDate)) {
            String message = String.format("Start date %s must be valid and not after end date %s", startDate, endDate);
            throw new IllegalArgumentException(message);
        }

        this.startDate = startDate;
    }
}
