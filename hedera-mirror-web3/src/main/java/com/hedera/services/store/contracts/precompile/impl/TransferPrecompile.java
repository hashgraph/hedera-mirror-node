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

import com.hedera.services.store.contracts.precompile.CryptoTransferWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class TransferPrecompile extends AbstractWritePrecompile {

    protected CryptoTransferWrapper transferOp;

    private final boolean isLazyCreationEnabled;
    private ImpliedTransfers impliedTransfers;
    private int numLazyCreates;

    public TransferPrecompile(PrecompilePricingUtils pricingUtils, boolean isLazyCreationEnabled) {
        super(pricingUtils);
        this.isLazyCreationEnabled = isLazyCreationEnabled;
    }

    @Override
    public void body(Bytes input, UnaryOperator<byte[]> aliasResolver) {}

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        Objects.requireNonNull(transferOp, "`body` method should be called before `getMinimumFeeInTinybars`");
        long accumulatedCost = 0;
        final boolean customFees = impliedTransfers != null && impliedTransfers.hasAssessedCustomFees();
        // For fungible there are always at least two operations, so only charge half for each
        // operation
        final long ftTxCost = pricingUtils.getMinimumPriceInTinybars(
                        customFees
                                ? PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE_CUSTOM_FEES
                                : PrecompilePricingUtils.GasCostType.TRANSFER_FUNGIBLE,
                        consensusTime)
                / 2;
        // NFTs are atomic, one line can do it.
        final long nonFungibleTxCost = pricingUtils.getMinimumPriceInTinybars(
                customFees
                        ? PrecompilePricingUtils.GasCostType.TRANSFER_NFT_CUSTOM_FEES
                        : PrecompilePricingUtils.GasCostType.TRANSFER_NFT,
                consensusTime);
        for (final var transfer : transferOp.tokenTransferWrappers()) {
            accumulatedCost += transfer.fungibleTransfers().size() * ftTxCost;
            accumulatedCost += transfer.nftExchanges().size() * nonFungibleTxCost;
        }

        // add the cost for transferring hbars
        // Hbar transfer is similar to fungible tokens so only charge half for each operation
        final long hbarTxCost =
                pricingUtils.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.TRANSFER_HBAR, consensusTime)
                        / 2;
        accumulatedCost += transferOp.transferWrapper().hbarTransfers().size() * hbarTxCost;
        if (isLazyCreationEnabled && numLazyCreates > 0) {
            final var lazyCreationFee = pricingUtils.getMinimumPriceInTinybars(GasCostType.CRYPTO_CREATE, consensusTime)
                    + pricingUtils.getMinimumPriceInTinybars(GasCostType.CRYPTO_UPDATE, consensusTime);
            accumulatedCost += numLazyCreates * lazyCreationFee;
        }
        return accumulatedCost;
    }

    @Override
    public void run(MessageFrame frame) {}
}
