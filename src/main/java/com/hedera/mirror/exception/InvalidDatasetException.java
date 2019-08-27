package com.hedera.mirror.exception;

/**
 * Invalid dataset such as an account balances dataset.
 */
public class InvalidDatasetException extends RuntimeException {
    public InvalidDatasetException() { super(); }

    public InvalidDatasetException(final String s) {
        super(s);
    }

    public InvalidDatasetException(final Throwable t) {
        super(t);
    }

    public InvalidDatasetException(final String s, final Throwable t) {
        super(s, t);
    }
}