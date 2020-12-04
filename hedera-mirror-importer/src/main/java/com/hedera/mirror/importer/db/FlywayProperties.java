package com.hedera.mirror.importer.db;

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

import javax.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("hedera.mirror.importer.db.flyway")
@Data
@Validated
public class FlywayProperties {

    @NotBlank
    private String baselineVersion = "0"; // set to 1.999.0 for v2 case

    private boolean baselineOnMigrate = true;

    private int connectRetries = 20;

    private boolean ignoreMissingMigrations = true;

    @NotBlank
    private String target = "1.999.0"; // set to 2.999.0 for v2 case
}
