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
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DISSOCIATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.DissociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

public abstract class AbstractDissociatePrecompile implements Precompile {

    protected final PrecompilePricingUtils pricingUtils;

    protected AbstractDissociatePrecompile(final PrecompilePricingUtils pricingUtils) {
        this.pricingUtils = pricingUtils;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, TransactionBody transactionBody) {
        return pricingUtils.getMinimumPriceInTinybars(DISSOCIATE, consensusTime);
    }

    @Override
    public RunResult run(MessageFrame frame, Store store, TransactionBody transactionBody) {
        final var accountId = Id.fromGrpcAccount(
                Objects.requireNonNull(transactionBody).getTokenDissociate().getAccount());

        final var dissociateLogic = new DissociateLogic();

        final var validity = dissociateLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        dissociateLogic.dissociate(
                accountId.asEvmAddress(),
                transactionBody.getTokenDissociate().getTokensList().stream()
                        .map(EntityIdUtils::asTypedEvmAddress)
                        .toList(),
                store);

        return new EmptyRunResult();
    }

    // TODO: Move to SyntheticTxnFactory
    protected TransactionBody.Builder createDissociate(final Dissociation dissociation) {
        final var builder = TokenDissociateTransactionBody.newBuilder();

        builder.setAccount(dissociation.accountId());
        builder.addAllTokens(dissociation.tokenIds());

        return TransactionBody.newBuilder().setTokenDissociate(builder);
    }
}
