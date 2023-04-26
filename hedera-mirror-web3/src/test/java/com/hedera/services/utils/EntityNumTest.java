package com.hedera.services.utils;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static org.junit.jupiter.api.Assertions.*;

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
}
