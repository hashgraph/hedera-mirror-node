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

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NullableStringSerializerTest {
    @Mock
    JsonGenerator jsonGenerator;

    @Test
    void testEmptyString() throws Exception {
        // when
        new NullableStringSerializer().serialize("", jsonGenerator, null);

        // then
        verify(jsonGenerator).writeString(NullableStringSerializer.NULLABLE_STRING_REPLACEMENT);
    }

    @Test
    void testString() throws Exception {
        // when
        new NullableStringSerializer().serialize("Mirror Node", jsonGenerator, null);

        // then
        verify(jsonGenerator).writeString("Mirror Node");
    }
}
