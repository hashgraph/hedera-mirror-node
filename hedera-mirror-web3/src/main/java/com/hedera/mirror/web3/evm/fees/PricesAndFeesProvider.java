package com.hedera.mirror.web3.evm.fees;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;

public interface PricesAndFeesProvider {
    FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at);

    ExchangeRate rate(Timestamp at) throws InvalidProtocolBufferException;

    long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at);
}
