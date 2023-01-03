package com.hedera.mirror.importer.exception;

public class FieldInaccessibleException extends ImporterException {
    public FieldInaccessibleException(Throwable throwable) {
        super(throwable);
    }
}
