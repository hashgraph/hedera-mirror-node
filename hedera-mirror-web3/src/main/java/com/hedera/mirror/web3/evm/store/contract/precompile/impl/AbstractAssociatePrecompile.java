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

package com.hedera.mirror.web3.evm.store.contract.precompile.impl;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKEN;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_ASSOCIATE_TOKENS;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.ASSOCIATE;

import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.EmptyResult;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Set;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractAssociatePrecompile implements Precompile {

    protected final PrecompilePricingUtils pricingUtils;

    protected AbstractAssociatePrecompile(final PrecompilePricingUtils pricingUtils) {
        this.pricingUtils = pricingUtils;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return pricingUtils.getMinimumPriceInTinybars(ASSOCIATE, consensusTime);
    }

    // TODO implement run logic
    @Override
    public RunResult run(MessageFrame frame, final StackedStateFrames<Object> stackedStateFrames) {
        //        final var accountId =
        //                Id.fromGrpcAccount(Objects.requireNonNull(associateOp).accountId());

        // --- Execute the transaction and capture its results ---
        //        final var associateLogic = new AssociateLogic(stackedStateFrames);
        //
        //        associateLogic.associate(accountId,
        // associateOp.tokenIds().stream().map(EntityIdUtils::asTypedEvmAddress).toList());

        return new EmptyResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_ASSOCIATE_TOKEN, ABI_ID_ASSOCIATE_TOKENS);
    }
}
