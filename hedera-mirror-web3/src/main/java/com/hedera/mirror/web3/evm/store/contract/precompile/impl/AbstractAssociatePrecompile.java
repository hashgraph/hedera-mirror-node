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

import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.ASSOCIATE;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.EmptyRunResult;
import com.hedera.mirror.web3.evm.store.contract.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.AssociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractAssociatePrecompile implements Precompile {

    protected final PrecompilePricingUtils pricingUtils;
    protected final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    protected AbstractAssociatePrecompile(
            final PrecompilePricingUtils pricingUtils, final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.pricingUtils = pricingUtils;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime) {
        return pricingUtils.getMinimumPriceInTinybars(ASSOCIATE, consensusTime);
    }

    @Override
    public RunResult run(
            MessageFrame frame,
            final StackedStateFrames<Object> stackedStateFrames,
            final TransactionBody transactionBody) {
        final var accountId = Id.fromGrpcAccount(
                Objects.requireNonNull(transactionBody).getTokenAssociate().getAccount());

        // --- Execute the transaction and capture its results ---
        final var associateLogic = new AssociateLogic(stackedStateFrames, mirrorNodeEvmProperties);

        associateLogic.associate(
                accountId.asEvmAddress(),
                transactionBody.getTokenAssociate().getTokensList().stream()
                        .map(EntityIdUtils::asTypedEvmAddress)
                        .toList());

        return new EmptyRunResult();
    }

    protected TransactionBody.Builder createAssociate(final Association association) {
        final var builder = TokenAssociateTransactionBody.newBuilder();

        builder.setAccount(association.accountId());
        builder.addAllTokens(association.tokenIds());

        return TransactionBody.newBuilder().setTokenAssociate(builder);
    }
}
