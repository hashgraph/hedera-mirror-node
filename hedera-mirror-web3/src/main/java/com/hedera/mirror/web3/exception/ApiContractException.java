package com.hedera.mirror.web3.exception;

import com.hedera.mirror.common.exception.MirrorNodeException;

public class ApiContractException extends MirrorNodeException {

    public ApiContractException(String message) {
        super(message);
    }
}
