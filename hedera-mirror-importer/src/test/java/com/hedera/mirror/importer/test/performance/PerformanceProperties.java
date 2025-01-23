/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.test.performance;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hedera.mirror.importer.test.performance")
@Data
public class PerformanceProperties {

    @Valid
    private DownloaderPerformanceProperties downloader = new DownloaderPerformanceProperties();

    @Valid
    private ParserPerformanceProperties parser = new ParserPerformanceProperties();

    @NotNull
    @Valid
    private Map<String, List<PerformanceScenarioProperties>> scenarios = Map.of();

    public enum SubType {
        STANDARD,
        TOKEN_TRANSFER
    }

    @Data
    @Validated
    public static class DownloaderPerformanceProperties {

        private boolean enabled = true;

        @DurationMin(millis = 1)
        @NotNull
        private Duration latency = Duration.ofMillis(250L);

        @NotBlank
        private String scenario;
    }

    @Data
    @Validated
    public static class ParserPerformanceProperties {

        private boolean enabled = true;

        @DurationMin(millis = 1)
        @NotNull
        private Duration latency = Duration.ofSeconds(2L);

        @NotBlank
        private String scenario;
    }

    @Data
    @Validated
    public static class PerformanceScenarioProperties {

        private String description;

        @DurationMin(seconds = 1L)
        @NotNull
        private Duration duration = Duration.ofSeconds(10L);

        private boolean enabled = true;

        @NotNull
        @Valid
        private List<PerformanceTransactionProperties> transactions = List.of();

        public String getDescription() {
            if (description != null) {
                return description;
            }
            return transactions.stream()
                    .map(PerformanceTransactionProperties::getDescription)
                    .collect(Collectors.joining(", "));
        }
    }

    @Data
    @Validated
    public static class PerformanceTransactionProperties {

        @Min(1)
        private int entities = 1;

        @NotNull
        private SubType subType = SubType.STANDARD;

        @Min(1)
        private int tps = 10_000;

        @NotNull
        private TransactionType type = TransactionType.CONSENSUSSUBMITMESSAGE;

        public String getDescription() {
            var name = subType != SubType.STANDARD ? subType : type;
            return tps + " " + name;
        }
    }
}
