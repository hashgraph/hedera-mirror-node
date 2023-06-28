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

package com.hedera.services.fees.calculation.token.txns;

import static com.hedera.services.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.usage.token.TokenDissociateUsage;
import com.hedera.services.hapi.fees.usage.EstimatorFactory;
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.services.hapi.utils.fees.SigValueObj;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.BiFunction;
import javax.inject.Inject;

public class TokenDissociateResourceUsage extends AbstractTokenResourceUsage implements TxnResourceUsageEstimator {
    private static final BiFunction<TransactionBody, TxnUsageEstimator, TokenDissociateUsage> factory =
            TokenDissociateUsage::newEstimate;

    @Inject
    public TokenDissociateResourceUsage(final EstimatorFactory estimatorFactory) {
        super(estimatorFactory);
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasTokenDissociate();
    }

    @Override
    public FeeData usageGiven(final TransactionBody txn, final SigValueObj svo, final Store store) throws Exception {
        final var op = txn.getTokenDissociate();
        final var account = store.getAccount(EntityIdUtils.asTypedEvmAddress(op.getAccount()), OnMissing.DONT_THROW);
        if (account == null) {
            return FeeData.getDefaultInstance();
        } else {
            final var sigUsage =
                    new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
            final var estimate = factory.apply(txn, estimatorFactory.get(sigUsage, txn, ESTIMATOR_UTILS));
            return estimate.get();
        }
    }
}
