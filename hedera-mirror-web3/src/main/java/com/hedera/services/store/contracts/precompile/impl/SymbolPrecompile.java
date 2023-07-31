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

import static java.util.Objects.requireNonNull;

import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.proxy.RedirectTarget;
import com.hedera.node.app.service.evm.store.contracts.utils.DescriptorUtils;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.codec.TokenSymbolResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class SymbolPrecompile extends AbstractReadOnlyPrecompile {

    public SymbolPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, encoder, pricingUtils);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_ERC_SYMBOL);
    }

    @Override
    public Bytes getSuccessResultFor(final RunResult runResult) {
        final var symbolResult = (TokenSymbolResult) runResult;
        return encoder.encodeSymbol(symbolResult.symbol());
    }

    @Override
    public RunResult run(final MessageFrame frame, final TransactionBody transactionBody) {
        requireNonNull(transactionBody, "`body` method should be called before `run`");

        final var target = getRedirectTarget(frame);
        final var store = ((HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater()).getStore();
        final var token = store.getToken(target.token(), OnMissing.THROW);

        return new TokenSymbolResult(token.getSymbol());
    }

    private static RedirectTarget getRedirectTarget(final MessageFrame frame) {
        final RedirectTarget target;
        try {
            target = DescriptorUtils.getRedirectTarget(frame.getInputData());
        } catch (final Exception e) {
            throw new InvalidTransactionException(ResponseCodeEnum.ERROR_DECODING_BYTESTRING);
        }
        return target;
    }
}
