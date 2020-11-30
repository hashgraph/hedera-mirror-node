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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Builder(toBuilder = true)
@ConfigurationProperties("hedera.mirror.importer.db")
@ConstructorBinding
public class DBProperties {
    @NotBlank
    private String host = "";

    private boolean loadBalance = true;

    @NotBlank
    private String name = "";

    @NotBlank
    private String password = "";

    @Min(0)
    private int port = 5432;

    @NotBlank
    private String restPassword = "";

    @NotBlank
    private String restUsername = "";

    @NotBlank
    private String username = "";

    @NotBlank
    private String flywayBaseline = "1.999.0";
}
