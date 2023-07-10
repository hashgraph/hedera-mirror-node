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

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.EMPTY_KEY;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.store.contracts.precompile.codec.ApproveWrapper;
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import lombok.NonNull;

public class SyntheticTxnFactory {

    public static final String AUTO_MEMO = "auto-created account";
    private static final String LAZY_MEMO = "lazy-created account";
    private static final long THREE_MONTHS_IN_SECONDS = 7776000L;

    public TransactionBody.Builder createMint(final MintWrapper mintWrapper) {
        final var builder = TokenMintTransactionBody.newBuilder();

        builder.setToken(mintWrapper.tokenType());
        if (mintWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllMetadata(mintWrapper.metadata());
        } else {
            builder.setAmount(mintWrapper.amount());
        }

        return TransactionBody.newBuilder().setTokenMint(builder);
    }

    public TransactionBody.Builder createHollowAccount(final ByteString alias, final long balance) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.setKey(asKeyUnchecked(EMPTY_KEY)).setAlias(alias).setMemo(LAZY_MEMO);
        return TransactionBody.newBuilder().setCryptoCreateAccount(baseBuilder.build());
    }

    public TransactionBody.Builder createAccount(
            final ByteString alias, final Key key, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance);
        baseBuilder.setKey(key).setAlias(alias).setMemo(AUTO_MEMO);

        if (maxAutoAssociations > 0) {
            baseBuilder.setMaxAutomaticTokenAssociations(maxAutoAssociations);
        }
        return TransactionBody.newBuilder().setCryptoCreateAccount(baseBuilder.build());
    }

    private CryptoCreateTransactionBody.Builder createAccountBase(final long balance) {
        return CryptoCreateTransactionBody.newBuilder()
                .setInitialBalance(balance)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(THREE_MONTHS_IN_SECONDS));
    }

    public TransactionBody.Builder createAssociate(final Association association) {
        final var builder = TokenAssociateTransactionBody.newBuilder();

        builder.setAccount(association.accountId());
        builder.addAllTokens(association.tokenIds());

        return TransactionBody.newBuilder().setTokenAssociate(builder);
    }

    public TransactionBody.Builder createDissociate(final Dissociation dissociation) {
        final var builder = TokenDissociateTransactionBody.newBuilder();

        builder.setAccount(dissociation.accountId());
        builder.addAllTokens(dissociation.tokenIds());

        return TransactionBody.newBuilder().setTokenDissociate(builder);
    }

    public TransactionBody.Builder createFungibleApproval(
            @NonNull final ApproveWrapper approveWrapper, @NonNull Id ownerId) {
        return createNonfungibleApproval(approveWrapper, ownerId, null);
    }

    public TransactionBody.Builder createNonfungibleApproval(
            final ApproveWrapper approveWrapper, @Nullable final Id ownerId, @Nullable final Id operatorId) {
        final var builder = CryptoApproveAllowanceTransactionBody.newBuilder();
        if (approveWrapper.isFungible()) {
            var tokenAllowance = TokenAllowance.newBuilder()
                    .setTokenId(approveWrapper.tokenId())
                    .setOwner(Objects.requireNonNull(ownerId).asGrpcAccount())
                    .setSpender(approveWrapper.spender())
                    .setAmount(approveWrapper.amount().longValueExact());
            builder.addTokenAllowances(tokenAllowance.build());
        } else {
            final var op = NftAllowance.newBuilder()
                    .setTokenId(approveWrapper.tokenId())
                    .setSpender(approveWrapper.spender())
                    .addSerialNumbers(approveWrapper.serialNumber().longValueExact());
            if (ownerId != null) {
                op.setOwner(ownerId.asGrpcAccount());
                if (!ownerId.equals(operatorId)) {
                    op.setDelegatingSpender(Objects.requireNonNull(operatorId).asGrpcAccount());
                }
            }
            builder.addNftAllowances(op.build());
        }
        return TransactionBody.newBuilder().setCryptoApproveAllowance(builder);
    }

    public TransactionBody.Builder createDeleteAllowance(final ApproveWrapper approveWrapper, final Id owner) {
        final var builder = CryptoDeleteAllowanceTransactionBody.newBuilder();
        builder.addAllNftAllowances(List.of(NftRemoveAllowance.newBuilder()
                        .setOwner(owner.asGrpcAccount())
                        .setTokenId(approveWrapper.tokenId())
                        .addAllSerialNumbers(
                                List.of(approveWrapper.serialNumber().longValueExact()))
                        .build()))
                .build();
        return TransactionBody.newBuilder().setCryptoDeleteAllowance(builder);
    }
}
