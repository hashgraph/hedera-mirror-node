package com.hedera.mirror.web3.exception;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.io.Serial;

public class ResourceLimitException extends InvalidTransactionException {

    @Serial
    private static final long serialVersionUID = -5647430220816450850L;

    public ResourceLimitException(ResponseCodeEnum responseCode) {
        super(responseCode);
    }
}
