package com.hedera.mirror.common.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LongWeiBarToStringSerializerTest {
    private static final Long defaultGas = 1234567890123L;
    @Mock
    JsonGenerator jsonGenerator;

    @Test
    void testNull() throws Exception {
        // when
        LongWeiBarToStringSerializer.INSTANCE.serialize(null, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNull();
    }

    @Test
    void testEmpty() throws Exception {
        // when
        LongWeiBarToStringSerializer.INSTANCE.serialize(0L, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNumber(0L);
    }

    @Test
    void testWeiBar() throws Exception {
        // when
        LongWeiBarToStringSerializer.INSTANCE.serialize(defaultGas, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNumber(123L);
    }
}
