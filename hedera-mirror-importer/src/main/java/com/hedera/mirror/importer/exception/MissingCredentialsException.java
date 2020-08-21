package com.hedera.mirror.importer.exception;

public class MissingCredentialsException extends ImporterException{

    private static final long serialVersionUID = 121078402562575433L;

    public MissingCredentialsException(String message) {
        super(message);
    }
}
