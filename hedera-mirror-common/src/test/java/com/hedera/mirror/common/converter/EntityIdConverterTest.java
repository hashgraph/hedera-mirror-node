/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.converter.EntityIdConverter.INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class EntityIdConverterTest {

    @Test
    void testToDatabaseColumn() {
        Assertions.assertThat(INSTANCE.convertToDatabaseColumn(null)).isNull();
        Assertions.assertThat(INSTANCE.convertToDatabaseColumn(EntityId.of(10L, 10L, 10L)))
                .isEqualTo(2814792716779530L);
    }

    @Test
    void testToEntityAttribute() {
        assertThat(INSTANCE.convertToEntityAttribute(null)).isNull();
        assertThat(INSTANCE.convertToEntityAttribute(9223372036854775807L))
                .isEqualTo(EntityId.of(32767L, 65535L, 4294967295L));
    }
}
