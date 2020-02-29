package com.hedera.mirror.importer.exception;


public class ParserSQLException extends ImporterException {

    private static final long serialVersionUID = 5216154273755649844L;

    public ParserSQLException(String message) {
        super(message);
    }

    public ParserSQLException(Throwable throwable) {
        super(throwable);
    }

    public ParserSQLException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
