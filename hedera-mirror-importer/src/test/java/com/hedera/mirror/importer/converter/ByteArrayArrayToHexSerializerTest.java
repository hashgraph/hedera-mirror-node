/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.converter;

import static com.hedera.mirror.importer.converter.ByteArrayArrayToHexSerializer.INSTANCE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.core.JsonGenerator;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ByteArrayArrayToHexSerializerTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @SneakyThrows
    @Test
    void testEmptyArray() {
        INSTANCE.serialize(new byte[][] {}, jsonGenerator, null);
        verify(jsonGenerator).writeString("{}");
    }

    @SneakyThrows
    @Test
    void testMultipleElementArray() {
        INSTANCE.serialize(new byte[][] {{0xa}, {0x1, 0xd}, null, {}}, jsonGenerator, null);
        verify(jsonGenerator).writeString("{\"\\\\x0a\",\"\\\\x010d\",null,\"\\\\x\"}");
    }

    @SneakyThrows
    @Test
    void testNull() {
        INSTANCE.serialize(null, jsonGenerator, null);
        verifyNoInteractions(jsonGenerator);
    }

    @SneakyThrows
    @Test
    void testSingleElementArray() {
        INSTANCE.serialize(new byte[][] {{0xa}}, jsonGenerator, null);
        verify(jsonGenerator).writeString("{\"\\\\x0a\"}");
    }
}
