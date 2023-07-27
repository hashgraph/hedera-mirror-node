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
import com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractReadOnlyPrecompile implements Precompile {
    private static final long MINIMUM_GAS_COST = 100L;

    protected final SyntheticTxnFactory syntheticTxnFactory;
    protected final EncodingFacade encoder;

    protected final PrecompilePricingUtils pricingUtils;

    protected AbstractReadOnlyPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.encoder = encoder;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, final BodyParams bodyParams) {
        return syntheticTxnFactory.createTransactionCall(1L, input);
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime, final TransactionBody transactionBody) {
        return MINIMUM_GAS_COST;
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        return new EmptyRunResult();
    }

    @Override
    public long getGasRequirement(
            final long blockTimestamp,
            final Builder transactionBody,
            final Store store,
            final HederaEvmContractAliases mirrorEvmContractAliases) {
        final var now = Timestamp.newBuilder().setSeconds(blockTimestamp).build();
        return pricingUtils.computeViewFunctionGas(now, MINIMUM_GAS_COST, store);
    }
}
