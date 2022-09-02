package com.hedera.mirror.web3.evm;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import javax.inject.Named;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;

@Named
@RequiredArgsConstructor
public class BasicFcfsUsagePrices implements UsagePricesProvider {

    private static final long DEFAULT_FEE = 100_000L;

    private static final Logger log = LogManager.getLogger(BasicFcfsUsagePrices.class);

    Timestamp currFunctionUsagePricesExpiry;
    Timestamp nextFunctionUsagePricesExpiry;

    Map<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices;
    Map<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices;

    @Override
    public Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at) {
        try {
            Map<HederaFunctionality, Map<SubType, FeeData>> functionUsagePrices =
                    applicableUsagePrices(at);
            Map<SubType, FeeData> usagePrices = functionUsagePrices.get(function);
            Objects.requireNonNull(usagePrices);
            return usagePrices;
        } catch (Exception e) {
            log.debug(
                    "Default usage price will be used, no specific usage prices available for"
                            + " function {} @ {}!",
                    function,
                    Instant.ofEpochSecond(at.getSeconds(), at.getNanos()));
        }
        return DEFAULT_RESOURCE_PRICES;
    }

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return pricesGiven(function, at).get(DEFAULT);
    }

    private Map<HederaFunctionality, Map<SubType, FeeData>> applicableUsagePrices(Timestamp at) {
        if (onlyNextScheduleApplies(at)) {
            return nextFunctionUsagePrices;
        } else {
            return currFunctionUsagePrices;
        }
    }

    private boolean onlyNextScheduleApplies(Timestamp at) {
        return at.getSeconds() >= currFunctionUsagePricesExpiry.getSeconds()
                && at.getSeconds() < nextFunctionUsagePricesExpiry.getSeconds();
    }


    private static final FeeComponents DEFAULT_PROVIDER_RESOURCE_PRICES =
            FeeComponents.newBuilder()
                    .setMin(DEFAULT_FEE)
                    .setMax(DEFAULT_FEE)
                    .setConstant(0)
                    .setBpt(0)
                    .setVpt(0)
                    .setRbh(0)
                    .setSbh(0)
                    .setGas(0)
                    .setTv(0)
                    .setBpr(0)
                    .setSbpr(0)
                    .build();

    public static final Map<SubType, FeeData> DEFAULT_RESOURCE_PRICES =
            Map.of(
                    DEFAULT,
                    FeeData.newBuilder()
                            .setNetworkdata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .setNodedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .setServicedata(DEFAULT_PROVIDER_RESOURCE_PRICES)
                            .build());


}
