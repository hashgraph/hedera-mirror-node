package com.hedera.mirror.api.contract.exception;

import com.hedera.mirror.common.exception.MirrorNodeException;

public class ApiContractException extends MirrorNodeException {

    public ApiContractException(String message) {
        super(message);
    }
}
