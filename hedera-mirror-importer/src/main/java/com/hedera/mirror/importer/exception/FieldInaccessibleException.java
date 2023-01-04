package com.hedera.mirror.importer.exception;

@SuppressWarnings("java:S110")
public class FieldInaccessibleException extends ImporterException {
    public FieldInaccessibleException(Throwable throwable) {
        super(throwable);
    }
}
