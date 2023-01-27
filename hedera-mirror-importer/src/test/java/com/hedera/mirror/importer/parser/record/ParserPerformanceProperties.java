package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.mirror.common.domain.transaction.TransactionType;

@ConfigurationProperties("hedera.mirror.importer.parser.record.performance")
@Data
public class ParserPerformanceProperties {

    @NotNull
    private Duration duration = Duration.ofSeconds(60L);

    @NotNull
    private List<PerformanceTransactionProperties> transactions = Collections.emptyList();

    @Data
    public static class PerformanceTransactionProperties {

        @Min(1)
        private int entities = 10;

        @Min(0)
        private int tps = 100;

        @NotNull
        private TransactionType type;
    }
}
