/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.test.e2e.acceptance.config;

import static com.hedera.mirror.test.e2e.acceptance.config.RestProperties.URL_PREFIX;

import jakarta.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "hedera.mirror.test.acceptance.rest-java")
@Data
@Named
@RequiredArgsConstructor
@Validated
public class RestJavaProperties {

    private String baseUrl;

    private boolean enabled = false;

    public String getBaseUrl() {
        if (baseUrl != null && !baseUrl.endsWith(URL_PREFIX)) {
            return baseUrl + URL_PREFIX;
        }
        return baseUrl;
    }
}
