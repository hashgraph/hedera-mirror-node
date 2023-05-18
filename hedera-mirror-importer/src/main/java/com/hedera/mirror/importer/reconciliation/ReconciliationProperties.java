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

package com.hedera.mirror.importer.reconciliation;

import com.hedera.mirror.importer.util.Utility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("hedera.mirror.importer.reconciliation")
class ReconciliationProperties {

    @NotBlank
    private String cron = "0 0 0 * * *"; // Every day at midnight

    @DurationMin(millis = 0)
    private Duration delay = Duration.ofSeconds(1L);

    private boolean enabled = true;

    @NotNull
    private Instant endDate = Utility.MAX_INSTANT_LONG;

    private RemediationStrategy remediationStrategy = RemediationStrategy.FAIL;

    @NotNull
    private Instant startDate = Instant.EPOCH;

    // We can't rely upon the NFT count in the balance file and there's not an easy way to just reconcile fungible
    private boolean token = false;

    public void setStartDate(Instant startDate) {
        if (startDate == null || startDate.isAfter(endDate)) {
            String message = String.format("Start date %s must be valid and not after end date %s", startDate, endDate);
            throw new IllegalArgumentException(message);
        }

        this.startDate = startDate;
    }

    public enum RemediationStrategy {
        ACCUMULATE, // Continue processing after transfer failures without resetting balances for the next iteration
        FAIL, // Halt processing on any reconciliation failure
        RESET, // Continue processing after transfer failures with corrected balances
    }
}
