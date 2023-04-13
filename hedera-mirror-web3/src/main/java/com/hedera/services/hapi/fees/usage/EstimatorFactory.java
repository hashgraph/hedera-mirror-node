package com.hedera.services.hapi.fees.usage;

import com.hederahashgraph.api.proto.java.TransactionBody;

@FunctionalInterface
public interface EstimatorFactory {
    TxnUsageEstimator get(SigUsage sigUsage, TransactionBody txn, EstimatorUtils utils);
}
