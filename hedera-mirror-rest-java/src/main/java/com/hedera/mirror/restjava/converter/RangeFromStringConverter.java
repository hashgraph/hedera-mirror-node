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

package com.hedera.mirror.restjava.converter;

import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import jakarta.inject.Named;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.util.StringUtils;

@Named
@ConfigurationPropertiesBinding
public class RangeFromStringConverter implements Converter<String, Range<Long>> {
    @Override
    public Range<Long> convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }

        var cleanedSource = source.replaceAll("\\s", "");
        return PostgreSQLGuavaRangeType.ofString(cleanedSource, Long::parseLong, Long.class);
    }
}
