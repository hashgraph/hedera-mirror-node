package com.hedera.services.utils;

import com.hedera.mirror.common.domain.entity.EntityId;

import com.hedera.services.store.models.Id;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals(expected, EntityNum.fromAccountId(EntityIdUtils.asAccount("0.0.123")));
        assertEquals(expected, EntityNum.fromTokenId(EntityIdUtils.asToken("0.0.123")));
        assertEquals(expected, EntityNum.fromContractId(EntityIdUtils.asContract("0.0.123")));
        assertEquals(expected, EntityNum.fromModel(new Id(0, 0, 123)));
        assertEquals(expected, EntityNum.fromEvmAddress(Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(0,0,123)))));
    }

    @Test
    void factoriesWorkForInvalidShard() {
        assertEquals(MISSING_NUM, EntityNum.fromAccountId(EntityIdUtils.asAccount("1.0.123")));
        assertEquals(MISSING_NUM, EntityNum.fromTokenId(EntityIdUtils.asToken("1.0.123")));
        assertEquals(MISSING_NUM, EntityNum.fromContractId(EntityIdUtils.asContract("1.0.123")));
        assertEquals(MISSING_NUM, EntityNum.fromModel(new Id(1, 0, 123)));
        assertEquals(
                new EntityNum(123),
                EntityNum.fromEvmAddress(Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(0,0,123)))));
    }

    @Test
    void factoriesWorkForInvalidRealm() {
        assertEquals(MISSING_NUM, EntityNum.fromAccountId(EntityIdUtils.asAccount("0.1.123")));
        assertEquals(MISSING_NUM, EntityNum.fromTokenId(EntityIdUtils.asToken("0.1.123")));
        assertEquals(MISSING_NUM, EntityNum.fromContractId(EntityIdUtils.asContract("0.1.123")));
        assertEquals(MISSING_NUM, EntityNum.fromModel(new Id(0, 1, 123)));
        assertEquals(new EntityNum(123),
                EntityNum.fromEvmAddress(Address.wrap(Bytes.wrap(EntityIdUtils.asEvmAddress(0,0,123)))));
    }

    @Test
    void viewsWork() {
        final var subject = EntityNum.fromInt(123);

        assertEquals(123, subject.toGrpcAccountId().getAccountNum());
        assertEquals(123, subject.toGrpcTokenId().getTokenNum());
        assertEquals(123, subject.toGrpcScheduleId().getScheduleNum());
        assertEquals(new EntityId(0, 0, 123), subject.toEntityId());
        assertTrue(subject.toIdString().endsWith(".123"));
    }

    @Test
    void viewsWorkEvenForNegativeNumCodes() {
        final long realNum = (long) Integer.MAX_VALUE + 10;

        final var subject = EntityNum.fromLong(realNum);

        assertEquals(realNum, subject.toGrpcAccountId().getAccountNum());
        assertEquals(realNum, subject.toGrpcTokenId().getTokenNum());
        assertEquals(realNum, subject.toGrpcScheduleId().getScheduleNum());
        assertTrue(subject.toIdString().endsWith("." + realNum));
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
