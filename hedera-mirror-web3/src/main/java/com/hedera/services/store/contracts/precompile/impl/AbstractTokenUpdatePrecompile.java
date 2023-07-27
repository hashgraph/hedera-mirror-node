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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.UPDATE;

import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;

/**
 * This class is a modified copy of AbstractTokenUpdatePrecompile from hedera-services repo.
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. The run method is only implemented in the extending precompiles. This approach eliminates
 *     the necessity for the switch statement currently used in hedera services. Moreover, it facilitates
 *     more stateless behaviour by avoiding storing of the update type operations in this class
 */
public abstract class AbstractTokenUpdatePrecompile extends AbstractWritePrecompile {

    protected AbstractTokenUpdatePrecompile(
            PrecompilePricingUtils pricingUtils, SyntheticTxnFactory syntheticTxnFactory) {
        super(pricingUtils, syntheticTxnFactory);
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, TransactionBody transactionBody) {
        return pricingUtils.getMinimumPriceInTinybars(UPDATE, consensusTime);
    }

    protected enum UpdateType {
        UPDATE_TOKEN_KEYS,
        UPDATE_TOKEN_INFO,
        UPDATE_TOKEN_EXPIRY
    }
}
