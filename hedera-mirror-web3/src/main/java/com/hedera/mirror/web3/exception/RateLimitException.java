package com.hedera.mirror.web3.exception;

import com.hedera.mirror.common.exception.MirrorNodeException;

public class RateLimitException extends MirrorNodeException {

    public RateLimitException(String message) {
        super(message);
    }
}
