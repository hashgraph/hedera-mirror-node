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

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

/**
 * This class is a modified copy of AbstractGrantRevokeKycPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Run method is modified to return {@link RunResult}, so that getSuccessResultFor is based on this record
 *  4. Run method is modified to accept {@link Store} as a parameter, so that we abstract from the internal state that is used for the execution
 */
public abstract class AbstractGrantRevokeKycPrecompile extends AbstractWritePrecompile {

    protected AbstractGrantRevokeKycPrecompile(
            SyntheticTxnFactory syntheticTxnFactory, PrecompilePricingUtils pricingUtils) {
        super(pricingUtils, syntheticTxnFactory);
    }
}
