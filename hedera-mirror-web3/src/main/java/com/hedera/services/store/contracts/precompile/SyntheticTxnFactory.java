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

import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenTransferList.Builder;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyntheticTxnFactory {

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
