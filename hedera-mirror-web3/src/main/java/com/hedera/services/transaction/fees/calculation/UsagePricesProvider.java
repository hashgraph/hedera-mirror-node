package com.hedera.services.transaction.fees.calculation;

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Map;

/**
 * Defines a type able to provide the prices (in tinyCents) that must
 * be paid for various resources to perform each Hedera operation.
 */
public interface UsagePricesProvider {

    /**
     * Called during startup to give the type an opportunity to
     * materialize its price schedules.
     *
     * @throws IllegalStateException if the schedules are not loaded
     */
    void loadPriceSchedules();

    /**
     * Returns the prices in tinyCents that are likely to be
     * required to consume various resources while processing
     * the given operation at the given time. (In principle, the
     * price schedules could change in the interim.)
     *
     * @param function the operation of interest
     * @param at the expected consensus time for the operation
     * @return the estimated prices
     */
    FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at);

    /**
     * Returns the prices in tinyCents that are likely to be
     * required to consume various resources while processing
     * the given operation at the given time. (In principle, the
     * price schedules could change in the interim.)
     *
     * @param function the operation of interest
     * @param at the expected consensus time for the operation
     * @return the estimated prices
     */
    Map<SubType, FeeData> pricesGiven(HederaFunctionality function, Timestamp at);

}
