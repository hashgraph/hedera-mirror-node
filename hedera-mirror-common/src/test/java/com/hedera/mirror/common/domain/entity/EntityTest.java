package com.hedera.mirror.common.domain.entity;

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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import org.junit.jupiter.api.Test;

class EntityTest {

    @Test
    void nullCharacters() {
        Entity entity = new Entity();
        entity.setMemo("abc" + (char) 0);
        assertThat(entity.getMemo()).isEqualTo("abc�");
    }

    @Test
    void history() {
        Entity entity = new Entity();
        assertThat(entity.getTimestampRange()).isNull();
        assertThat(entity.getTimestampLower()).isNull();
        assertThat(entity.getTimestampUpper()).isNull();

        Range<Long> timestampRangeLower = Range.atLeast(1L);
        entity.setTimestampRange(timestampRangeLower);
        assertThat(entity.getTimestampRange()).isEqualTo(timestampRangeLower);
        assertThat(entity.getTimestampLower()).isEqualTo(timestampRangeLower.lowerEndpoint());
        assertThat(entity.getTimestampUpper()).isNull();

        entity.setTimestampUpper(2L);
        assertThat(entity.getTimestampUpper()).isEqualTo(2L);

        Range<Long> timestampRangeUpper = Range.atMost(1L);
        entity.setTimestampRange(timestampRangeUpper);
        assertThat(entity.getTimestampRange()).isEqualTo(timestampRangeUpper);
        assertThat(entity.getTimestampLower()).isNull();
        assertThat(entity.getTimestampUpper()).isEqualTo(timestampRangeUpper.upperEndpoint());

        entity.setTimestampLower(0L);
        assertThat(entity.getTimestampLower()).isEqualTo(0L);
    }
}
