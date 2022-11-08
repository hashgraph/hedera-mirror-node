package com.hedera.mirror.web3.service.eth;

import com.hedera.mirror.web3.controller.PricesAndFeesImpl;

import com.hederahashgraph.api.proto.java.Timestamp;
import lombok.RequiredArgsConstructor;
import javax.inject.Named;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;

@Named
@RequiredArgsConstructor
public class FeeScheduleService implements EthService<Object, String> {

    static final String METHOD = "eth_feeSchedule";

    private final PricesAndFeesImpl pricesAndFees;


    @Override
    public String getMethod() {
        return METHOD;
    }

    @Override
    public String get(Object request) {
        var timeNow = Timestamp.newBuilder().setSeconds(1655378100001826921L).build();

        var result = pricesAndFees.defaultPricesGiven(ContractCall, timeNow);

        return String.valueOf(result);
    }
}
