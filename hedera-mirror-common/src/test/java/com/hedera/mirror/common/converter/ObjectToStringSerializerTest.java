/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.converter.ObjectToStringSerializer.INSTANCE;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObjectToStringSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @Test
    void serialize() throws IOException {
        INSTANCE.serialize(Entity.builder().id(1).type("unknown").build(), jsonGenerator, null);
        verify(jsonGenerator).writeString("{\"id\":1,\"type\":\"unknown\"}");
    }

    @Test
    void serializeList() throws IOException {
        var entities = List.of(
                Entity.builder().id(1).type("unknown").build(),
                Entity.builder().id(2).type(null).build());
        INSTANCE.serialize(entities, jsonGenerator, null);
        verify(jsonGenerator).writeString("[{\"id\":1,\"type\":\"unknown\"},{\"id\":2,\"type\":null}]");
    }

    @Test
    void serializeNull() throws IOException {
        INSTANCE.serialize(null, jsonGenerator, null);
        verify(jsonGenerator).writeString("null");
    }

    @AllArgsConstructor
    @Builder
    @Data
    private static class Entity {
        private long id;
        private String type;
    }
}
