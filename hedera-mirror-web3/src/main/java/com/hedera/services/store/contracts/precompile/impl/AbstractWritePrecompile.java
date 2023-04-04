/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.TransactionBody;

import com.hedera.services.store.contracts.MirrorState;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

public abstract class AbstractWritePrecompile implements Precompile {
    protected static final String FAILURE_MESSAGE = "Invalid full prefix for %s precompile!";
    protected final MirrorState mirrorState;
    protected final PrecompilePricingUtils pricingUtils;
    protected TransactionBody.Builder transactionBody;

    protected AbstractWritePrecompile(
            final MirrorState mirrorState,
            final PrecompilePricingUtils pricingUtils) {
        this.mirrorState = mirrorState;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        return pricingUtils.computeGasRequirement(blockTimestamp, this, transactionBody);
    }
}
