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
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.ASSOCIATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.AssociateLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of AssociatePrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Run method is modified to return {@link RunResult}, so that getSuccessResultFor is based on this record
 *  4. Run method is modified to accept {@link Store} as a parameter, so that we abstract from the internal state that is used for the execution
 *  4. Run method does not handle signature verification and has a logic based on modified {@link AssociateLogic} that accepts the {@link Store} as a parameter
 */
public abstract class AbstractAssociatePrecompile implements Precompile {

    protected final PrecompilePricingUtils pricingUtils;
    protected final AssociateLogic associateLogic;

    protected AbstractAssociatePrecompile(
            final PrecompilePricingUtils pricingUtils, final AssociateLogic associateLogic) {
        this.pricingUtils = pricingUtils;
        this.associateLogic = associateLogic;
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, final TransactionBody transactionBody) {
        return pricingUtils.getMinimumPriceInTinybars(ASSOCIATE, consensusTime);
    }

    @Override
    public RunResult run(MessageFrame frame, final Store store, final TransactionBody transactionBody) {
        final var accountId = Id.fromGrpcAccount(
                Objects.requireNonNull(transactionBody).getTokenAssociate().getAccount());

        // --- Execute the transaction and capture its results ---

        final var validity = associateLogic.validateSyntax(transactionBody);
        validateTrue(validity == OK, validity);

        associateLogic.associate(
                accountId.asEvmAddress(),
                transactionBody.getTokenAssociate().getTokensList().stream()
                        .map(EntityIdUtils::asTypedEvmAddress)
                        .toList(),
                store);

        return new EmptyRunResult();
    }

    protected TransactionBody.Builder createAssociate(final Association association) {
        final var builder = TokenAssociateTransactionBody.newBuilder();

        builder.setAccount(association.accountId());
        builder.addAllTokens(association.tokenIds());

        return TransactionBody.newBuilder().setTokenAssociate(builder);
    }
}
