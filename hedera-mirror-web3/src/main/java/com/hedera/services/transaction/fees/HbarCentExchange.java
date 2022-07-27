package com.hedera.services.transaction.fees;

import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.Timestamp;

public interface HbarCentExchange {
    ExchangeRate rate(Timestamp at);

}
