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

package com.hedera.mirror.restjava.common;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.InvalidEntityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EntityIdParameterTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "0.1.x",
                "x.1",
                "0.1.2.3",
                "a",
                "a.b.c",
                "-1.-1.-1",
                "-1",
                "0.0.-1",
                "100000.65535.000000001",
                "100000.000000001",
                "0x",
                "0x00000001000000000000000200000000000000034",
                "0x2540be3f6001fffffffffffff001fffffffffffff",
                "0x10000000000000000000000000000000000000000",
                "9223372036854775807"
            })
    @DisplayName("EntityId parse from string tests, negative cases")
    void entityParseFromStringFailure(String inputId) {
        assertThrows(IllegalArgumentException.class, () -> EntityIdParameter.parseId(inputId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.4294967296", "32768.65536.4294967296"})
    @DisplayName("EntityId parse from string tests, negative cases for ID having valid format")
    void testInvalidEntity(String input) {
        assertThrows(InvalidEntityException.class, () -> EntityIdParameter.parseId(input));
    }

    @Test
    @DisplayName("EntityId parse from string tests")
    void entityParseFromString() {
        assertThat(EntityId.of(0, 0, 0))
                .isEqualTo(EntityIdParameter.parseId("0.0.0").num());
        assertThat(EntityId.of(0, 0, 0))
                .isEqualTo(EntityIdParameter.parseId("0").num());
        assertThat(EntityId.of(0, 0, 4294967295L))
                .isEqualTo(EntityIdParameter.parseId("0.0.4294967295").num());
        assertThat(EntityId.of(0, 65535, 1))
                .isEqualTo(EntityIdParameter.parseId("65535.000000001").num());
        assertThat(EntityId.of(32767, 65535, 4294967295L))
                .isEqualTo(EntityIdParameter.parseId("32767.65535.4294967295").num());
        assertThat(EntityId.of(0, 0, 4294967295L))
                .isEqualTo(EntityIdParameter.parseId("4294967295").num());
        assertThat(EntityId.of(0, 0, 1))
                .isEqualTo(EntityIdParameter.parseId("0.1").num());
        assertThat(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1})
                .isEqualTo(EntityIdParameter.parseId("0x0000000000000000000000000000000000000001")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1})
                .isEqualTo(EntityIdParameter.parseId("0000000000000000000000000000000000000001")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3})
                .isEqualTo(EntityIdParameter.parseId("0x0000000100000000000000020000000000000003")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3})
                .isEqualTo(EntityIdParameter.parseId("0000000100000000000000020000000000000003")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3})
                .isEqualTo(EntityIdParameter.parseId("1.2.0000000100000000000000020000000000000003")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 127, -1, 0, 0, 0, 0, 0, 0, -1, -1, 0, 0, 0, 0, -1, -1, -1, -1})
                .isEqualTo(EntityIdParameter.parseId("0x00007fff000000000000ffff00000000ffffffff")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, -122, -5, 27})
                .isEqualTo(EntityIdParameter.parseId("0.0.000000000000000000000000000000000186Fb1b")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, -122, -5, 27})
                .isEqualTo(EntityIdParameter.parseId("0.000000000000000000000000000000000186Fb1b")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, -122, -5, 27})
                .isEqualTo(EntityIdParameter.parseId("000000000000000000000000000000000186Fb1b")
                        .evmAddress());
        assertThat(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, -122, -5, 27})
                .isEqualTo(EntityIdParameter.parseId("0x000000000000000000000000000000000186Fb1b")
                        .evmAddress());
        assertThat(new byte[] {0, 2, 17, 11, 90})
                .isEqualTo(EntityIdParameter.parseId("AABBCC22").alias());
        assertThat(new byte[] {0, 2, 17, 11, 90})
                .isEqualTo(EntityIdParameter.parseId("0.AABBCC22").alias());
        assertThat(new byte[] {0, 2, 17, 11, 90})
                .isEqualTo(EntityIdParameter.parseId("0.1.AABBCC22").alias());
    }

    @Test
    @DisplayName("EntityId parse from string tests")
    void entityIdValueOf() {
        assertThat(new EntityIdParameter(EntityId.of(0, 0, 0), null, null, 0L, 0L, EntityIdType.NUM))
                .isEqualTo(EntityIdParameter.valueOf("0.0.0"));
        assertThat(EntityIdParameter.EMPTY).isEqualTo(EntityIdParameter.valueOf(null));
        assertThat(new EntityIdParameter(null, null, new byte[] {0, 2, 17, 11, 90}, 0L, 0L, EntityIdType.ALIAS))
                .isEqualTo(EntityIdParameter.valueOf("AABBCC22"));
        assertThat(new EntityIdParameter(
                        null,
                        new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, -122, -5, 27},
                        null,
                        0L,
                        0L,
                        EntityIdType.EVMADDRESS))
                .isEqualTo(EntityIdParameter.valueOf("0x000000000000000000000000000000000186Fb1b"));
    }
}
