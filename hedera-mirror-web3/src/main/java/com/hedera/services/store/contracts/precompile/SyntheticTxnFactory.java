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
import com.hedera.services.store.contracts.precompile.codec.Association;
import com.hedera.services.store.contracts.precompile.codec.BurnWrapper;
import com.hedera.services.store.contracts.precompile.codec.Dissociation;
import com.hedera.services.store.contracts.precompile.codec.MintWrapper;
import com.hedera.services.store.contracts.precompile.codec.WipeWrapper;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenTransferList.Builder;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public TransactionBody.Builder createBurn(final BurnWrapper burnWrapper) {
        final var builder = TokenBurnTransactionBody.newBuilder();

        builder.setToken(burnWrapper.tokenType());
        if (burnWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllSerialNumbers(burnWrapper.serialNos());
        } else {
            builder.setAmount(burnWrapper.amount());
        }
        return TransactionBody.newBuilder().setTokenBurn(builder);
    }

    public TransactionBody.Builder createWipe(final WipeWrapper wipeWrapper) {
        final var builder = TokenWipeAccountTransactionBody.newBuilder();

        builder.setToken(wipeWrapper.token());
        builder.setAccount(wipeWrapper.account());
        if (wipeWrapper.type() == NON_FUNGIBLE_UNIQUE) {
            builder.addAllSerialNumbers(wipeWrapper.serialNumbers());
        } else {
            builder.setAmount(wipeWrapper.amount());
        }

        return TransactionBody.newBuilder().setTokenWipe(builder);
    }

    /**
     * Given a list of {@link TokenTransferWrapper}s, where each wrapper gives changes scoped to a
     * particular {@link TokenID}, returns a synthetic {@code CryptoTransfer} whose {@link
     * CryptoTransferTransactionBody} consolidates the wrappers.
     *
     * <p>If two wrappers both refer to the same token, their transfer lists are merged as specified
     * in the {@link SyntheticTxnFactory#mergeTokenTransfers(TokenTransferList.Builder,
     * TokenTransferList.Builder)} helper method.
     *
     * @param wrappers the wrappers to consolidate in a synthetic transaction
     * @return the synthetic transaction
     */
    public TransactionBody.Builder createCryptoTransfer(final List<TokenTransferWrapper> wrappers) {
        final var opBuilder = CryptoTransferTransactionBody.newBuilder();
        if (wrappers.size() == 1) {
            opBuilder.addTokenTransfers(wrappers.get(0).asGrpcBuilder());
        } else if (wrappers.size() > 1) {
            final List<TokenTransferList.Builder> builders = new ArrayList<>();
            final Map<TokenID, Builder> listBuilders = new HashMap<>();
            for (final TokenTransferWrapper wrapper : wrappers) {
                final var builder = wrapper.asGrpcBuilder();
                final var merged =
                        listBuilders.merge(builder.getToken(), builder, SyntheticTxnFactory::mergeTokenTransfers);
                /* If merge() returns a builder other than the one we just created, it is already in the list */
                if (merged == builder) {
                    builders.add(builder);
                }
            }
            builders.forEach(opBuilder::addTokenTransfers);
        }
        return TransactionBody.newBuilder().setCryptoTransfer(opBuilder);
    }

    /**
     * Given a {@link TransferWrapper},
     *
     * <p>returns a synthetic {@code CryptoTransfer} whose {@link CryptoTransferTransactionBody}
     * consolidates the wrappers which embodies hbar transfers between accounts.
     *
     * @param wrapper the wrappers to consolidate in a synthetic transaction
     * @return the synthetic transaction
     */
    public TransactionBody createCryptoTransferForHbar(final TransferWrapper wrapper) {
        final var opBuilder = CryptoTransferTransactionBody.newBuilder();
        if (!wrapper.hbarTransfers().isEmpty()) {
            opBuilder.setTransfers(wrapper.asGrpcBuilder());
        }
        return TransactionBody.newBuilder().setCryptoTransfer(opBuilder).build();
    }

    /**
     * Merges the fungible and non-fungible exchanges from one token transfer list into another. (Of
     * course, at most one of these merges can be sensible; a token cannot be both fungible _and_
     * non-fungible.)
     *
     * <p>Fungible exchanges are "merged" by summing up all the amount fields for each unique
     * account id that appears in either list. NFT exchanges are "merged" by checking that each
     * exchange from either list appears at most once.
     *
     * @param to the builder to merge source exchanges into
     * @param from a source of fungible exchanges and NFT exchanges
     * @return the consolidated target builder
     */
    static TokenTransferList.Builder mergeTokenTransfers(
            final TokenTransferList.Builder to, final TokenTransferList.Builder from) {
        mergeFungible(from, to);
        mergeNonFungible(from, to);
        return to;
    }

    private static void mergeFungible(final TokenTransferList.Builder from, final TokenTransferList.Builder to) {
        for (int i = 0, n = from.getTransfersCount(); i < n; i++) {
            final var transfer = from.getTransfers(i);
            final var targetId = transfer.getAccountID();
            var merged = false;
            for (int j = 0, m = to.getTransfersCount(); j < m; j++) {
                final var transferBuilder = to.getTransfersBuilder(j);
                if (targetId.equals(transferBuilder.getAccountID())) {
                    final var prevAmount = transferBuilder.getAmount();
                    transferBuilder.setAmount(prevAmount + transfer.getAmount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                to.addTransfers(transfer);
            }
        }
    }

    private static void mergeNonFungible(final TokenTransferList.Builder from, final TokenTransferList.Builder to) {
        for (int i = 0, n = from.getNftTransfersCount(); i < n; i++) {
            final var fromExchange = from.getNftTransfersBuilder(i);
            var alreadyPresent = false;
            for (int j = 0, m = to.getNftTransfersCount(); j < m; j++) {
                final var toExchange = to.getNftTransfersBuilder(j);
                if (areSameBuilder(fromExchange, toExchange)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                to.addNftTransfers(fromExchange);
            }
        }
    }

    static boolean areSameBuilder(final NftTransfer.Builder a, final NftTransfer.Builder b) {
        return a.getSerialNumber() == b.getSerialNumber()
                && a.getSenderAccountID().equals(b.getSenderAccountID())
                && a.getReceiverAccountID().equals(b.getReceiverAccountID());
    }
}
