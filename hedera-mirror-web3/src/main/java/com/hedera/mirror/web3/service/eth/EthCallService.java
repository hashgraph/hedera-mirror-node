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

    @Override
    public String get(final EthRpcCallBody body) {
        //get params from body as hex

        //implement a repository object for Entity to map the account by it's shard,realm,num props
        //and build dto object which will be passed to the custom evmTxProcessor.executeEtch(..);

        //get the result from the txnProcessor.getOutput().toHexString();

        //temporary output

        return "0x" + ResponseCodeEnum.SUCCESS_VALUE;
    }
}
