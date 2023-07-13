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

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.FunctionParam;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.WipeLogic;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;

import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.WIPE_FUNGIBLE;

/**
 * This class is a modified copy of WipeFungiblePrecompile from hedera-services repo.
 * <p>
 * Differences with the original:
 * 1. Implements a modified {@link Precompile} interface
 * 2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 * 3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 * 4. getMinimumFeeInTinybars method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 */
public class WipeFungiblePrecompile extends AbstractWipePrecompile {
    protected final SyntheticTxnFactory syntheticTxnFactory;

    public WipeFungiblePrecompile(
            final PrecompilePricingUtils pricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WipeLogic wipeLogic) {
        super(pricingUtils, wipeLogic);
        this.syntheticTxnFactory = syntheticTxnFactory;
    }

    public static WipeWrapper getWipeWrapper(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver, @NonNull final SystemContractAbis abi) {
        final Tuple decodedArguments = decodeFunctionCall(input, abi.selector, abi.decoder);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);
        final var fungibleAmount = (long) decodedArguments.get(2);

        return WipeWrapper.forFungible(tokenID, accountID, fungibleAmount);
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var functionId = ((FunctionParam) bodyParams).functionId();
        final var wipeAbi =
                switch (functionId) {
                    case AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE -> SystemContractAbis.WIPE_TOKEN_ACCOUNT_V1;
                    case AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2 -> SystemContractAbis.WIPE_TOKEN_ACCOUNT_V2;
                    default -> throw new IllegalArgumentException("invalid selector to wipe precompile");
                };
        final var wipeOp = getWipeWrapper(input, aliasResolver, wipeAbi);
        return syntheticTxnFactory.createWipe(wipeOp);
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(WIPE_FUNGIBLE, consensusTime);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE, AbiConstants.ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE_V2);
    }
}
