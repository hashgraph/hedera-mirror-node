package com.hedera.services.fees.calculation;

import com.hedera.services.context.primitives.StateView;

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;

public interface QueryResourceUsageEstimator {
    /**
     * Flags whether the estimator applies to the given query.
     *
     * @param query the query in question
     * @return if the estimator applies
     */
    boolean applicableTo(final Query query);

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world and response type.
     *
     * @param query the query in question
     * @param view the state of the world
     * @param type the response type of the given query
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    default FeeData usageGivenType(final Query query, final StateView view, final ResponseType type) {
        return usageGiven(query, view);
    }

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world.
     *
     * @param query the query in question
     * @param view the state of the world
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    default FeeData usageGiven(final Query query, final StateView view) {
        return usageGiven(query, view, null);
    }

    /**
     * Returns the estimated resource usage for the given query relative to the given state of the
     * world, with a context for storing any information that may be useful for by later stages of
     * the query answer flow.
     *
     * @param query the query in question
     * @param view the state of the world
     * @param queryCtx the context of the query being answered
     * @return the estimated resource usage
     * @throws NullPointerException or analogous if the estimator does not apply to the query
     */
    FeeData usageGiven(final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx);


}
