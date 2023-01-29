package com.hedera.mirror.graphql.util;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.domain.entity.EntityType.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.graphql.viewmodel.Account;
import com.hedera.mirror.graphql.viewmodel.EntityIdInput;
import com.hedera.mirror.graphql.viewmodel.HbarUnit;

class GraphQlUtilsTest {

    @CsvSource(nullValues = "null", textBlock = """
              null,     1,                         1
              HBAR,     null,                      null
              TINYBAR,  1,                         1
              MICROBAR, 100,                       1
              MILIBAR,  100_000,                   1
              HBAR,     100_000_000,               1
              KILOBAR,  100_000_000_000,           1
              MEGABAR,  100_000_000_000_000,       1
              GIGABAR,  100_000_000_000_000_000,   1
              TINYBAR,  5_000_000_000_000_000_000, 5_000_000_000_000_000_000
              MICROBAR, 5_000_000_000_000_000_000, 50_000_000_000_000_000
              MILIBAR,  5_000_000_000_000_000_000, 50_000_000_000_000
              HBAR,     5_000_000_000_000_000_000, 50_000_000_000
              KILOBAR,  5_000_000_000_000_000_000, 50_000_000
              MEGABAR,  5_000_000_000_000_000_000, 50_000
              GIGABAR,  5_000_000_000_000_000_000, 50
            """)
    @ParameterizedTest
    void convertCurrency(HbarUnit unit, Long input, Long output) {
        assertThat(GraphQlUtils.convertCurrency(unit, input)).isEqualTo(output);
    }

    @Test
    void getId() {
        var node = new Account();
        node.setId(Base64.getEncoder().encodeToString("Account:1".getBytes(StandardCharsets.UTF_8)));
        long value = GraphQlUtils.getId(node, i -> Long.parseLong(i.get(0)));
        assertThat(value).isEqualTo(1L);
    }

    @Test
    void toEntityId() {
        var input = EntityIdInput.builder().withShard(0L).withRealm(0L).withNum(3L).build();
        assertThat(GraphQlUtils.toEntityId(input))
                .isNotNull()
                .returns(input.getShard(), EntityId::getShardNum)
                .returns(input.getRealm(), EntityId::getRealmNum)
                .returns(input.getNum(), EntityId::getEntityNum)
                .returns(UNKNOWN, EntityId::getType);
    }

    @Test
    void validateOneOf() {
        GraphQlUtils.validateOneOf("a");
        assertThatThrownBy(() -> GraphQlUtils.validateOneOf()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GraphQlUtils.validateOneOf("a", "b")).isInstanceOf(IllegalArgumentException.class);
    }
}
