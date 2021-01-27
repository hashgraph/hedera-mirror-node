package com.hedera.mirror.importer.parser.record.entity.repository;

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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;

@ConditionOnEntityRecordParser
@ConfigurationProperties("hedera.mirror.importer.parser.record.entity.repository")
@Data
public class RepositoryProperties {

    private boolean enabled = false;
}
