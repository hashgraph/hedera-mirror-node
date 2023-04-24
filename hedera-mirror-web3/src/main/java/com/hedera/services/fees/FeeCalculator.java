package com.hedera.services.fees;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.hapi.utils.fees.FeeObject;

import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.accessors.TxnAccessor;

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import java.util.Map;

/**
 * Defines a type able to calculate the fees required for various operations within Hedera Services.
 */
public interface FeeCalculator {

    FeeObject estimatePayment(Query query, FeeData usagePrices, StateView view, Timestamp at, ResponseType type);

    long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at);

    FeeObject computeFee(TxnAccessor accessor, JKey payerKey, StateView view, Instant consensusTime);
}
