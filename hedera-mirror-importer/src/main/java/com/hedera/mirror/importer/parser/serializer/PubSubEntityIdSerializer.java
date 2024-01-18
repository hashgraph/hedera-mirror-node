/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import java.io.IOException;

public class PubSubEntityIdSerializer extends JsonSerializer<EntityId> {

    private static final String SHARD_NUM = "shardNum";
    private static final String REALM_NUM = "realmNum";
    private static final String ENTITY_NUM = "entityNum";
    private static final String TYPE = "type";

    @Override
    public void serialize(EntityId entityId, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeStartObject();
        gen.writeFieldName(SHARD_NUM);
        gen.writeRawValue(String.valueOf(entityId.getShard()));
        gen.writeFieldName(REALM_NUM);
        gen.writeRawValue(String.valueOf(entityId.getRealm()));
        gen.writeFieldName(ENTITY_NUM);
        gen.writeRawValue(String.valueOf(entityId.getNum()));
        gen.writeFieldName(TYPE);
        gen.writeNumber(EntityType.UNKNOWN.ordinal());
        gen.writeEndObject();
    }
}
