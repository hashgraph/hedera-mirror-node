/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.hedera.mirror.common.domain.entity.EntityId;
import java.io.IOException;

@SuppressWarnings("java:S6548")
public class EntityIdDeserializer extends JsonDeserializer<EntityId> {

    public static final EntityIdDeserializer INSTANCE = new EntityIdDeserializer();

    @Override
    public EntityId deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
        Long value = jsonParser.readValueAs(Long.class);
        return value != null ? EntityId.of(value) : null;
    }
}
