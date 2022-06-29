package com.hedera.mirror.web3.evm.exception;

public abstract class EvmException extends RuntimeException {

    public EvmException(String message) {
        super(message);
    }

    public EvmException(Throwable throwable) {
        super(throwable);
    }

    public EvmException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
