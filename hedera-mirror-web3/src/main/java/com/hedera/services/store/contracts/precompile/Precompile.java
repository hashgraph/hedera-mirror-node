package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.Timestamp;

import com.hedera.services.utils.accessors.TxnAccessor;

public interface Precompile {

    // Customize fee charging
    long getMinimumFeeInTinybars(Timestamp consensusTime);

    default void addImplicitCostsIn(final TxnAccessor accessor) {
        // Most transaction types can compute their full Hedera fee from just an initial transaction
        // body; but
        // for a token transfer, we may need to recompute to charge for the extra work implied by
        // custom fees
    }
}
