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

import static com.hedera.mirror.common.converter.EntityIdDeserializer.INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdDeserializerTest {

    @Mock
    private JsonParser jsonParser;

    @Test
    void deserialize() throws IOException {
        doReturn(98L).when(jsonParser).readValueAs(Long.class);
        var actual = INSTANCE.deserialize(jsonParser, context());
        assertThat(actual).isEqualTo(EntityId.of(98L, EntityType.UNKNOWN));
    }

    @Test
    void deserializeNull() throws IOException {
        doReturn(null).when(jsonParser).readValueAs(Long.class);
        assertThat(INSTANCE.deserialize(jsonParser, context())).isNull();
    }

    private DeserializationContext context() {
        return new ObjectMapper().getDeserializationContext();
    }
}
