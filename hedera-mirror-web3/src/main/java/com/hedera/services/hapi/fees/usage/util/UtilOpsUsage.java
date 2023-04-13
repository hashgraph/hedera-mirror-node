package com.hedera.services.hapi.fees.usage.util;

import com.hedera.services.fees.usage.state.UsageAccumulator;
import com.hedera.services.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.services.hapi.fees.usage.SigUsage;

import javax.inject.Inject;

public class UtilOpsUsage {

    @Inject
    public UtilOpsUsage() {
        // Default constructor
    }

    public void prngUsage(
            final SigUsage sigUsage,
            final BaseTransactionMeta baseMeta,
            final UtilPrngMeta utilPrngMeta,
            final UsageAccumulator accumulator) {
        accumulator.resetForTransaction(baseMeta, sigUsage);
        var baseSize = utilPrngMeta.getMsgBytesUsed();
        accumulator.addBpt(baseSize);
    }
}
