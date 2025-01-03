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

package com.hedera.mirror.web3.convert;

import com.hedera.hapi.node.base.SemanticVersion;
import jakarta.inject.Named;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;

@Named
@ConfigurationPropertiesBinding
public class SemanticVersionConvertor implements Converter<String, SemanticVersion> {

    private final com.hedera.node.config.converter.SemanticVersionConverter delegate =
            new com.hedera.node.config.converter.SemanticVersionConverter();

    @Override
    public SemanticVersion convert(String source) {
        return delegate.convert(source);
    }
}
