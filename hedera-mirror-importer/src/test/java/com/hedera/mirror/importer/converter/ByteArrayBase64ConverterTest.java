package com.hedera.mirror.importer.converter;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.Test;

class ByteArrayBase64ConverterTest {
    ByteArrayBase64Converter converter = new ByteArrayBase64Converter();

    @Test
    void testForNulls() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void testToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(new byte[] {1, 2, 3})).isEqualTo(new byte[] {65, 81, 73, 68});
    }

    @Test
    void testToEntityAttribute() {
        assertThat(converter.convertToEntityAttribute(new byte[] {65, 81, 73, 68}))
                .isEqualTo(new byte[] {1, 2, 3});
    }
}
