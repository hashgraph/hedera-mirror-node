package com.hedera.mirror.web3.exception;

import com.hedera.mirror.common.exception.MirrorNodeException;

public abstract class EvmException extends MirrorNodeException {

    public EvmException(String message) {
        super(message);
    }
}
