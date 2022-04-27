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
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;

@ExtendWith(MockitoExtension.class)
class ByteArrayWeiBarToStringSerializerTest {
    private static final byte[] defaultGas = BigInteger.valueOf(1234567890123L).toByteArray();
    @Mock
    JsonGenerator jsonGenerator;

    @Test
    void testNull() throws Exception {
        // when
        ByteArrayWeiBarToStringSerializer.INSTANCE.serialize(null, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNull();
    }

    @Test
    void testEmpty() throws Exception {
        // when
        ByteArrayWeiBarToStringSerializer.INSTANCE.serialize(new byte[0], jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNull();
    }

    @Test
    void testWeiBar() throws Exception {
        // when
        ByteArrayWeiBarToStringSerializer.INSTANCE.serialize(defaultGas, jsonGenerator, null);

        // then
        Mockito.verify(jsonGenerator).writeNumber("\\x7b");
    }
}
