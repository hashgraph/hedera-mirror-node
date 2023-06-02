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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RangeToStringDeserializerTest {

    private static final RangeToStringDeserializer deserializer = new RangeToStringDeserializer();

    @Mock
    private JsonParser jsonParser;

    @ParameterizedTest
    @ValueSource(strings = {"[0,1]", "[0,1)", "(0,1]", "(0,1)", "[0,)", "(,1]", "empty"})
    void serialize(String text) throws IOException {
        Mockito.doReturn(text).when(jsonParser).readValueAs(String.class);
        Range<?> range = deserializer.deserialize(jsonParser, context());
        Assertions.assertThat(range).isEqualTo(PostgreSQLGuavaRangeType.longRange(text));
    }

    @Test
    void serializeNull() throws IOException {
        Mockito.doReturn(null).when(jsonParser).readValueAs(String.class);
        Assertions.assertThat(deserializer.deserialize(jsonParser, context())).isNull();
    }

    private DeserializationContext context() {
        return new ObjectMapper().getDeserializationContext();
    }
}
