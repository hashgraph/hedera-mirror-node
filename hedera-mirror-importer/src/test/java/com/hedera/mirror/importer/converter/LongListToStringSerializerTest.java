package com.hedera.mirror.importer.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LongListToStringSerializerTest {

    @Mock
    JsonGenerator jsonGenerator;

    private final LongListToStringSerializer serializer = new LongListToStringSerializer();

    @Test
    void testNull() throws IOException {
        // when
        serializer.serialize(null, jsonGenerator, null);

        // then
        verify(jsonGenerator, never()).writeString(anyString());
    }

    @Test
    void testEmptyList() throws IOException {
        // when
        serializer.serialize(Collections.emptyList(), jsonGenerator, null);

        // then
        verify(jsonGenerator, times(1)).writeString("{}");
    }

    @Test
    void testNonEmptyList() throws IOException {
        // when
        serializer.serialize(List.of(1L, 2L), jsonGenerator, null);

        // then
        verify(jsonGenerator, times(1)).writeString("{1,2}");
    }
}
