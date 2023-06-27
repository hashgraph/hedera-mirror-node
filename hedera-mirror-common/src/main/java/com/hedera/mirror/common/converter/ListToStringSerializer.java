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

package com.hedera.mirror.common.converter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("unchecked")
public class ListToStringSerializer extends JsonSerializer<List<?>> {

    public static final JsonSerializer<List<?>> INSTANCE = new ListToStringSerializer();
    private static final Class<?> HANDLED_TYPE = ListToStringSerializer.class;
    private static final String PREFIX = "{";
    private static final String SEPARATOR = ",";
    private static final String SUFFIX = "}";

    @Override
    public Class<List<?>> handledType() {
        return (Class<List<?>>) HANDLED_TYPE;
    }

    @Override
    public void serialize(List<?> list, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (list != null) {
            gen.writeString(PREFIX + StringUtils.join(list, SEPARATOR) + SUFFIX);
        }
    }
}
