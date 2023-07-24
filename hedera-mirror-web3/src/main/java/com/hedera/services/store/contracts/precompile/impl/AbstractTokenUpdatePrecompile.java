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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.UPDATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractTokenUpdatePrecompile extends AbstractWritePrecompile {

    protected UpdateType type;
    protected TokenUpdateLogic tokenUpdateLogic;

    protected final SyntheticTxnFactory syntheticTxnFactory;

    protected AbstractTokenUpdatePrecompile(
            PrecompilePricingUtils pricingUtils,
            TokenUpdateLogic tokenUpdateLogic,
            SyntheticTxnFactory syntheticTxnFactory) {
        super(pricingUtils);
        this.tokenUpdateLogic = tokenUpdateLogic;
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, TransactionBody transactionBody) {
        return pricingUtils.getMinimumPriceInTinybars(UPDATE, consensusTime);
    }

    @Override
    public RunResult run(MessageFrame frame, Store store, TransactionBody transactionBody) {
        final var validity = tokenUpdateLogic.validate(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        switch (type) {
            case UPDATE_TOKEN_INFO -> tokenUpdateLogic.updateToken(
                    transactionBody.getTokenUpdate(), frame.getBlockValues().getTimestamp(), store);
                // add the other cases
        }

        return new EmptyRunResult();
    }

    protected enum UpdateType {
        UPDATE_TOKEN_KEYS,
        UPDATE_TOKEN_INFO,
        UPDATE_TOKEN_EXPIRY
    }
}
