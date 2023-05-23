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

package com.hedera.mirror.importer.retention;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@ConfigurationProperties("hedera.mirror.importer.retention")
@Validated
public class RetentionProperties {

    @NotNull
    private Duration batchPeriod = Duration.ofDays(1L);

    private boolean enabled = false;

    @NotNull
    private Set<String> exclude = Collections.emptySet();

    @NotNull
    private Duration frequency = Duration.ofDays(1L);

    @NotNull
    private Set<String> include = Collections.emptySet();

    @NotNull
    private Duration period = Duration.ofDays(90L);

    public boolean shouldPrune(String table) {
        return (include.isEmpty() || include.contains(table)) && (exclude.isEmpty() || !exclude.contains(table));
    }
}
