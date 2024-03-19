/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.mapper;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.rest.model.TimestampRange;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommonMapperTest {

    private final CommonMapper commonMapper = Mappers.getMapper(CommonMapper.class);

    @Test
    void mapEntityId() {
        var entityId = com.hedera.mirror.common.domain.entity.EntityId.of("1.2.3");
        assertNull(commonMapper.mapEntityId((com.hedera.mirror.common.domain.entity.EntityId) null));
        assertThat(commonMapper.mapEntityId(entityId))
                .isEqualTo(EntityId.of(1L, 2L, 3L).toString());
    }

    @Test
    void mapEntityIdLong() {
        assertNull(commonMapper.mapEntityId((Long) null));
        assertNull(commonMapper.mapEntityId(0L));
    }

    @Test
    void mapRange() {
        var range = new TimestampRange();
        var now = System.nanoTime();
        range.setFrom("0.0");
        assertThat(commonMapper.mapRange(null)).isNull();
        assertThat(commonMapper.mapRange(Range.atLeast(0L)))
                .usingRecursiveComparison()
                .isEqualTo(range);
        range.setTo(String.valueOf(now / Math.pow(10, 9)));
        assertThat(commonMapper.mapRange(Range.openClosed(0L, now)))
                .usingRecursiveComparison()
                .isEqualTo(range);
    }
}
