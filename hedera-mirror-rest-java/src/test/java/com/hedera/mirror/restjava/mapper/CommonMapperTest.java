/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.rest.model.TimestampRange;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CommonMapperTest {

    private final CommonMapper commonMapper = Mappers.getMapper(CommonMapper.class);

    @Test
    void mapEntityId() {
        var entityId = com.hedera.mirror.common.domain.entity.EntityId.of("1.2.3");
        assertThat(commonMapper.mapEntityId((com.hedera.mirror.common.domain.entity.EntityId) null))
                .isNull();
        assertThat(commonMapper.mapEntityId(entityId)).isEqualTo(toEntityId(1L, 2L, 3L));
    }

    @Test
    void mapEntityIdLong() {
        assertThat(commonMapper.mapEntityId((Long) null)).isNull();
        assertThat(commonMapper.mapEntityId(0L)).isEqualTo(toEntityId(0L, 0L, 0L));
    }

    @Test
    void mapInstant() {
        var now = Instant.now();
        assertThat(commonMapper.mapInstant(null)).isNull();
        assertThat(commonMapper.mapInstant(0L)).isEqualTo(Instant.EPOCH);
        assertThat(commonMapper.mapInstant(DomainUtils.convertToNanosMax(now))).isEqualTo(now);
    }

    @Test
    void mapRange() {
        var range = new TimestampRange();
        range.setFrom(Instant.EPOCH.toString());
        assertThat(commonMapper.mapRange(null)).isNull();
        assertThat(commonMapper.mapRange(Range.atLeast(0L)))
                .usingRecursiveComparison()
                .isEqualTo(range);

        range.setTo(Instant.now().toString());
        assertThat(commonMapper.mapRange(
                        Range.openClosed(0L, DomainUtils.convertToNanosMax(Instant.parse(range.getTo())))))
                .usingRecursiveComparison()
                .isEqualTo(range);
    }

    private String toEntityId(Long shard, Long realm, Long num) {
        return shard + "." + realm + "." + num;
    }
}
