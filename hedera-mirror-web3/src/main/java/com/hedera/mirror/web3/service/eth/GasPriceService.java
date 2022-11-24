package com.hedera.mirror.web3.service.eth;

import com.hedera.mirror.web3.controller.PricesAndFeesImpl;

import com.hedera.services.evm.contracts.execution.PricesAndFeesProvider;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import lombok.RequiredArgsConstructor;
import javax.inject.Named;

import java.sql.Timestamp;
import java.time.Instant;

@Named
@RequiredArgsConstructor
public class GasPriceService implements EthService<Object, String> {

    static final String METHOD = "eth_gasPrice";

    private final PricesAndFeesImpl pricesAndFees;

    @Override
    public String getMethod() {
        return METHOD;
    }

    @Override
    public String get(Object request) {
        //1570809637249848002 - While debugging use this epoch when executing queries
        var timestamp = com.hederahashgraph.api.proto.java.Timestamp.newBuilder().setSeconds(157080963724L).setNanos(9848002).build();
        var timeNow = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());

        var result = pricesAndFees.currentGasPrice(timeNow, HederaFunctionality.EthereumTransaction);

        return String.valueOf(result);
    }
}
