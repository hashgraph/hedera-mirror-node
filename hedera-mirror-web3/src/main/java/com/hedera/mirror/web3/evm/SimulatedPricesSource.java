package com.hedera.mirror.web3.evm;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;

public class SimulatedPricesSource {

    public long currentGasPrice(final Instant now, final HederaFunctionality function) {
        return 0L;
    }
}
