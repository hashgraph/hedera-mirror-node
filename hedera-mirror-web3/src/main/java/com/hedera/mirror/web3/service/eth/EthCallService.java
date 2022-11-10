package com.hedera.mirror.web3.service.eth;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class EthCallService implements ApiContractEthService<EthRpcCallBody, String> {

    static final String CALL_METHOD = "call";

    @Override
    public String getMethod() {
        return CALL_METHOD;
    }

    /**
     * (Future work): Full implementation for the eth service will be added with future PRs once the evm-module lib is
     * completed with all required objects!
     */
    @Override
    public String get(final EthRpcCallBody body) {
        return "0x" + ResponseCodeEnum.SUCCESS_VALUE;
    }
}
