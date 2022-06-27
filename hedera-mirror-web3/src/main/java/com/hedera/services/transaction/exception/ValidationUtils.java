package com.hedera.services.transaction.exception;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * A minimalist collection of helpers to improve readability of code
 * that throws an {@code InvalidTransactionException}.
 */
public final class ValidationUtils {
    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static void validateTrue(final boolean flag, final ResponseCodeEnum code) {
        if (!flag) {
            throw new InvalidTransactionException(code);
        }
    }

    public static void validateTrueOrRevert(final boolean flag, final ResponseCodeEnum code) {
        if (!flag) {
            throw InvalidTransactionException.fromReverting(code, code.name());
        }
    }

    public static void validateTrue(final boolean flag, final ResponseCodeEnum code, final String failureMsg) {
        if (!flag) {
            throw new InvalidTransactionException(failureMsg, code);
        }
    }

    public static void validateFalse(final boolean flag, final ResponseCodeEnum code) {
        if (flag) {
            throw new InvalidTransactionException(code);
        }
    }

    public static void validateFalse(final boolean flag, final ResponseCodeEnum code, final String failureMsg) {
        if (flag) {
            throw new InvalidTransactionException(failureMsg, code);
        }
    }
}
