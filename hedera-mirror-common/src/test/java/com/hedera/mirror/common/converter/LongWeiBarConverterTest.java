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

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class LongWeiBarConverterTest {
    private static final LongWeiBarConverter converter = LongWeiBarConverter.INSTANCE;
    private static final Long defaultGas = 1234567890123L;

    @Test
    void testToDatabaseColumn() {
        Assertions.assertThat(converter.convertToDatabaseColumn(null)).isNull();
        Assertions.assertThat(converter.convertToDatabaseColumn(defaultGas))
                .isEqualTo(123L);
    }

    @Test
    void testToEntityAttribute() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute(defaultGas))
                .isEqualTo(defaultGas);
    }
}
