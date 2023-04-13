package com.hedera.services.fees.calculation;

import com.hederahashgraph.api.proto.java.TransactionBody;

public interface TxnResourceUsageEstimator {

    /**
     * Flags whether the estimator applies to the given transaction.
     *
     * @param txn the txn in question
     * @return if the estimator applies
     */
    boolean applicableTo(TransactionBody txn);

}
