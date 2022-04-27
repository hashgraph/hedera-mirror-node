package com.hedera.mirror.common.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import javax.persistence.AttributeConverter;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;

@javax.persistence.Converter
@ConfigurationPropertiesBinding
public class LongWeiBarConverter implements AttributeConverter<Long, Long> {
    public static final LongWeiBarConverter INSTANCE = new LongWeiBarConverter();
    public static final Long WEIBARS_TO_TINYBARS = 10_000_000_000L;

    @Override
    public Long convertToDatabaseColumn(Long weibar) {
        return weibar == null ? null : weibar / WEIBARS_TO_TINYBARS;
    }

    @Override
    public Long convertToEntityAttribute(Long tinyBar) {
        return tinyBar;
    }
}
