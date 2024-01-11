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

package com.hedera.services.utils;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static org.junit.jupiter.api.Assertions.*;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

class EntityNumTest {
    @Test
    void overridesJavaLangImpl() {
        final var v = 1_234_567;

        final var subject = new EntityNum(v);

        assertNotEquals(v, subject.hashCode());
    }

    @Test
    void equalsWorks() {
        final var a = new EntityNum(1);
        final var b = new EntityNum(2);
        final var c = a;

        assertNotEquals(a, b);
        assertNotEquals(null, a);
        assertNotEquals(new Object(), a);
        assertEquals(a, c);
    }

    @Test
    void returnsMissingNumForUnusableNum() {
        assertEquals(MISSING_NUM, EntityNum.fromLong(Long.MAX_VALUE));
    }

    @Test
    void factoriesWorkForValidShardRealm() {
        final var expected = EntityNum.fromInt(123);

        assertEquals(expected, EntityNum.fromLong(123L));
    }

    @Test
    void canGetLongValue() {
        final long realNum = (long) Integer.MAX_VALUE + 10;

        final var subject = EntityNum.fromLong(realNum);

        assertEquals(realNum, subject.longValue());
    }

    @Test
    void orderingSortsByValue() {
        int value = 100;

        final var base = new EntityNum(value);
        final var sameButDiff = EntityNum.fromInt(value);
        assertEquals(0, base.compareTo(sameButDiff));
        final var largerNum = new EntityNum(value + 1);
        assertEquals(-1, base.compareTo(largerNum));
        final var smallerNum = new EntityNum(value - 1);
        assertEquals(+1, base.compareTo(smallerNum));
    }

    @Test
    void factoriesWorkForInvalidShard() {
        assertEquals(MISSING_NUM, EntityNum.fromAccountId(IdUtils.asAccount("1.0.123")));
        assertEquals(
                new EntityNum(123),
                EntityNum.fromEvmAddress(Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(123)))));
    }
}
