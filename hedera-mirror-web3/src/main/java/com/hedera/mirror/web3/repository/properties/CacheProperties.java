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

package com.hedera.mirror.web3.repository.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.cache")
public class CacheProperties {

    @NotBlank
    private String contractState = "expireAfterWrite=5s,maximumSize=10000,recordStats";

    @NotBlank
    private String fee = "expireAfterWrite=10m,maximumSize=20,recordStats";

    @NotBlank
    private String entity = "expireAfterWrite=30s,maximumSize=10000,recordStats";

    @NotBlank
    private String token = "expireAfterWrite=2s,maximumSize=10000,recordStats";
}
