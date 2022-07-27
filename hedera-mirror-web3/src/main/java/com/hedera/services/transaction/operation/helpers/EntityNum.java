package com.hedera.services.transaction.operation.helpers;

import com.google.common.primitives.Longs;

import com.hedera.services.transaction.operation.util.MiscUtils;

import org.jetbrains.annotations.NotNull;

public class EntityNum implements Comparable<EntityNum> {
    public static final EntityNum MISSING_NUM = new EntityNum(0);
    public static final long MAX_NUM_ALLOWED = 0xFFFFFFFFL;

    private final int value;

    public EntityNum(int value) {
        this.value = value;
    }

    public static EntityNum fromLong(long l) {
        if (!isValidNum(l)) {
            return MISSING_NUM;
        }
        final var value = codeFromNum(l);
        return new EntityNum(value);
    }

    public static boolean isValidNum(long num) {
        return num >= 0 && num <= MAX_NUM_ALLOWED;
    }

    public static int codeFromNum(long num) {
        assertValid(num);
        return (int) num;
    }

    public static void assertValid(long num) {
        if (num < 0 || num > MAX_NUM_ALLOWED) {
            throw new IllegalArgumentException("Serial number " + num + " out of range!");
        }
    }

    public static EntityNum fromMirror(final byte[] evmAddress) {
        return EntityNum.fromLong(numFromEvmAddress(evmAddress));
    }

    public static long numFromEvmAddress(final byte[] bytes) {
        return Longs.fromBytes(
                bytes[12], bytes[13], bytes[14], bytes[15], bytes[16], bytes[17], bytes[18],
                bytes[19]);
    }

    @Override
    public int hashCode() {
        return (int) MiscUtils.perm64(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || EntityNum.class != o.getClass()) {
            return false;
        }

        var that = (EntityNum) o;

        return this.value == that.value;
    }

    @Override
    public String toString() {
        return "EntityNum{" + "value=" + value + '}';
    }

    @Override
    public int compareTo(@NotNull final EntityNum that) {
        return Integer.compare(this.value, that.value);
    }
}
