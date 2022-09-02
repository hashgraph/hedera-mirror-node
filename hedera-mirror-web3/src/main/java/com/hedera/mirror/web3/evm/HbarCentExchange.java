package com.hedera.mirror.web3.evm;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.Timestamp;
import javax.inject.Named;

@Named
public interface HbarCentExchange {
    ExchangeRate rate(Timestamp at);
}
