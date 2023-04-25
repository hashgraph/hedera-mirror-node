package com.hedera.services.fees.calculation;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.hapi.utils.fees.SigValueObj;

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

public interface TxnResourceUsageEstimator { // TODO move copy the implementation from services

    /**
     * Flags whether the estimator applies to the given transaction.
     *
     * @param txn the txn in question
     * @return if the estimator applies
     */
    boolean applicableTo(TransactionBody txn);

    /**
     * Returns the estimated resource usage for the given txn relative to the given state of the
     * world.
     *
     * @param txn the txn in question
     * @param sigUsage the signature usage
     * @param view the state of the world
     * @return the estimated resource usage
     * @throws Exception if the txn is malformed
     * @throws NullPointerException or analogous if the estimator does not apply to the txn
     */
    FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StateView view) throws Exception;
}
