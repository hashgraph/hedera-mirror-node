package com.hedera.mirror.web3.exception;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class InvalidTransactionException extends EvmException {

    public InvalidTransactionException(final ResponseCodeEnum responseCode) {
        super(responseCode.name());
    }
}
