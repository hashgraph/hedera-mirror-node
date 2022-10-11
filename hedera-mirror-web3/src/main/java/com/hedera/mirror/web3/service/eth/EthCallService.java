package com.hedera.mirror.web3.service.eth;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.units.bigints.UInt256;


@Named
@RequiredArgsConstructor
public class EthCallService implements ApiContractEthService<EthRpcCallBody, String> {

    static final String ETH_CALL_METHOD = "eth_call";

    @Override
    public String getMethod() {
        return ETH_CALL_METHOD;
    }

    @Override
    public String get(final EthRpcCallBody body) {
        //get params from body as hex

        //implement a repository object for Entity to map the account by it's shard,realm,num props
        //and build dto object which will be passed to the custom evmTxProcessor.executeEtch(..);


        //get the result from the txnProcessor.getOutput().toHexString();

        //temporary output for poc
        final var successResult = UInt256.valueOf(ResponseCodeEnum.SUCCESS_VALUE);
        return successResult.toHexString();
    }
}
