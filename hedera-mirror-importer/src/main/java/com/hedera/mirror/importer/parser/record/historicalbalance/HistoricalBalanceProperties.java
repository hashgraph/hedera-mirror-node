/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.historicalbalance;

import com.hedera.mirror.importer.downloader.balance.BalanceDownloaderProperties;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

@Data
@ConfigurationProperties("hedera.mirror.importer.parser.record.historical-balance")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@Validated
public class HistoricalBalanceProperties {

    private final BalanceDownloaderProperties balanceDownloaderProperties;

    private boolean enabled = true;

    @DurationMin(minutes = 2)
    @DurationUnit(ChronoUnit.MINUTES)
    @NotNull
    private Duration initialDelay = Duration.ofMinutes(2);

    /**
     * The minimum frequency between balance snapshots. The max value is 7 days, acts as the maximum interval between
     * two consecutive balance snapshots, so queries which use, e.g., 30 days timestamp range as an optimization will
     * not fail to find a snapshot.
     */
    @DurationMax(days = 7)
    @DurationMin(minutes = 15)
    @DurationUnit(ChronoUnit.MINUTES)
    @NotNull
    private Duration minFrequency = Duration.ofMinutes(15);

    private boolean tokenBalances = true;

    @DurationMin(seconds = 30)
    @DurationUnit(ChronoUnit.SECONDS)
    @NotNull
    private Duration transactionTimeout = Duration.ofMinutes(5);

    @PostConstruct
    void init() {
        if (balanceDownloaderProperties.isEnabled() && isEnabled()) {
            throw new IllegalArgumentException(
                    "The two configuration properties can't be both true: hedera.mirror.importer.downloader.balance.enabled and hedera.mirror.importer.parser.record.historicalBalance.enabled");
        }
    }
}
