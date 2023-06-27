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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hedera.mirror.common.domain.entity.EntityId;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S6548")
public class ObjectToStringSerializer extends JsonSerializer<Object> {

    public static final ObjectToStringSerializer INSTANCE = new ObjectToStringSerializer();
    public static final ObjectMapper OBJECT_MAPPER;

    static {
        var module = new SimpleModule();
        module.addDeserializer(EntityId.class, EntityIdDeserializer.INSTANCE);
        module.addSerializer(EntityId.class, EntityIdSerializer.INSTANCE);

        OBJECT_MAPPER = new ObjectMapper()
                .registerModule(module)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        // Configure hyperpersistence utils so that JsonBinaryType uses the same object mapper
        JsonConfiguration.INSTANCE.getObjectMapperWrapper().setObjectMapper(OBJECT_MAPPER);
    }

    @Override
    public void serialize(Object o, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        var json = OBJECT_MAPPER.writeValueAsString(o);
        gen.writeString(json);
    }
}
