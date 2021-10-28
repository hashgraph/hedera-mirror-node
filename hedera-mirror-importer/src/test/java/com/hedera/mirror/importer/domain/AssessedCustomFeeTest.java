package com.hedera.mirror.importer.domain;

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

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.util.EntityIdEndec;

class AssessedCustomFeeTest {

    @Test
    void defaultEffectivePayerAccountIds() {
        AssessedCustomFee assessedCustomFee = new AssessedCustomFee();
        assertThat(assessedCustomFee.getEffectivePayerAccountIds()).isEmpty();
    }

    @Test
    void setEffectivePayerEntityIds() {
        // given
        AssessedCustomFee assessedCustomFee = new AssessedCustomFee();
        List<Long> ids = List.of(1001L, 1002L);

        // when
        assessedCustomFee.setEffectivePayerEntityIds(ids.stream()
                .map(id -> EntityIdEndec.decode(id, EntityType.ACCOUNT))
                .collect(Collectors.toList()));

        // then
        assertThat(assessedCustomFee.getEffectivePayerAccountIds()).containsExactlyInAnyOrderElementsOf(ids);
    }
}
