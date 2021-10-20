package com.hedera.mirror.importer.parser.record.entity.sql;

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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;

@Data
@ConditionOnEntityRecordParser
@ConfigurationProperties("hedera.mirror.importer.parser.record.entity.sql")
public class SqlProperties {

    @Min(1)
    private int batchSize = 20_000;

    /**
     * The number of distinct tables persisted to during batch processing
     */
    @Max(25)
    @Min(1)
    private int connectionThreadsPoolSize = 20;

    private boolean enabled = true;

    private boolean parallelIngestion = false;
}
