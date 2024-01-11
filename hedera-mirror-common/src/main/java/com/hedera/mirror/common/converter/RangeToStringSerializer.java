/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings({"java:S6548", "unchecked"}) // Singletons are fine
public class RangeToStringSerializer extends JsonSerializer<Range<?>> {

    public static final RangeToStringSerializer INSTANCE = new RangeToStringSerializer();
    private static final Class<?> HANDLED_TYPE = Range.class;

    @Override
    public void serialize(Range<?> range, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (range != null) {
            gen.writeString(PostgreSQLGuavaRangeType.INSTANCE.asString(range));
        }
    }

    @Override
    public Class<Range<?>> handledType() {
        return (Class<Range<?>>) HANDLED_TYPE;
    }
}
