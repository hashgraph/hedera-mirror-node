/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.importer.cache")
public class CacheProperties {

    @NotBlank
    private String addressBook = "maximumSize=100,expireAfterWrite=5m,recordStats";

    @NotBlank
    private String alias = "maximumSize=100000,expireAfterAccess=30m,recordStats";

    private boolean enabled = true;

    @NotBlank
    private String timePartition = "maximumSize=50,expireAfterWrite=1d,recordStats";

    @NotBlank
    private String timePartitionOverlap = "maximumSize=50,expireAfterWrite=1d,recordStats";
}
