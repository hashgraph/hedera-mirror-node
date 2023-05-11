/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.fees.calculation;

import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.services.hapi.utils.fees.SigValueObj;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;

public interface TxnResourceUsageEstimator {

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
     * @param txn      the txn in question
     * @param sigUsage the signature usage
     * @param state
     * @return the estimated resource usage
     * @throws Exception            if the txn is malformed
     * @throws NullPointerException or analogous if the estimator does not apply to the txn
     */
    @SuppressWarnings("java:S112")
    FeeData usageGiven(TransactionBody txn, SigValueObj sigUsage, StackedStateFrames<?> state) throws Exception;
}
