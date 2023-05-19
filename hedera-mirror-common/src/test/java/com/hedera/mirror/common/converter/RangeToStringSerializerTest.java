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
import com.google.common.collect.Range;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RangeToStringSerializerTest {

    private static final RangeToStringSerializer serializer = new RangeToStringSerializer();

    @Mock
    private JsonGenerator jsonGenerator;

    @ParameterizedTest
    @ValueSource(strings = {"[0,1]", "[0,1)", "(0,1]", "(0,1)", "[0,)", "(,1]", "empty"})
    void serialize(String text) throws IOException {
        Range<Long> range = PostgreSQLGuavaRangeType.longRange(text);
        serializer.serialize(range, jsonGenerator, null);
        Mockito.verify(jsonGenerator).writeString(text);
    }

    @Test
    void serializeNull() throws IOException {
        serializer.serialize(null, jsonGenerator, null);
        Mockito.verify(jsonGenerator, Mockito.never()).writeString(ArgumentMatchers.anyString());
    }
}
