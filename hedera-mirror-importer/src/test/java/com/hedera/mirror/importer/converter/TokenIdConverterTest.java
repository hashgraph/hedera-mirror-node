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

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;

class TokenIdConverterTest {
    TokenIdConverter converter = new TokenIdConverter();

    @Test
    void testForNulls() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void testToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(EntityId.of(10L, 10L, 10L, EntityTypeEnum.TOKEN)))
                .isEqualTo(2814792716779530L);
    }

    @Test
    void testToEntityAttribute() {
        assertThat(converter.convertToEntityAttribute(9223372036854775807L))
                .isEqualTo(EntityId.of(32767L, 65535L, 4294967295L, EntityTypeEnum.TOKEN));
    }
}
