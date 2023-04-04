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
package com.hedera.services.txns.crypto;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

import com.hedera.services.store.contracts.MirrorState;

import com.hedera.services.utils.EntityIdUtils;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

public class DeleteAllowanceLogic {

    private final MirrorState mirrorState;
    private final List<UniqueToken> nftsTouched;

    @Inject
    public DeleteAllowanceLogic(MirrorState mirrorState) {
        this.mirrorState = mirrorState;
        this.nftsTouched = new ArrayList<>();
    }

    public void deleteAllowance(final List<NftRemoveAllowance> nftAllowancesList, final AccountID payer) {
        nftsTouched.clear();

        // --- Load models ---
        final Id payerId = Id.fromGrpcAccount(payer);
        final var payerAccount = mirrorState.loadAccount(payerId);

        // --- Do the business logic ---
        deleteNftSerials(nftAllowancesList, payerAccount);

        // --- Persist the owner accounts and nfts ---
        for (final var nft : nftsTouched) {
            mirrorState.persistNft(nft);
        }
    }

    /**
     * Clear spender on the provided nft serials. If the owner is not provided in any allowance, considers payer of the
     * transaction as owner while checking if nft is owned by owner.
     *
     * @param nftAllowances given nftAllowances
     * @param payerAccount  payer for the transaction
     */
    private void deleteNftSerials(final List<NftRemoveAllowance> nftAllowances, final Account payerAccount) {
        if (nftAllowances.isEmpty()) {
            return;
        }
        final var nfts = new ArrayList<UniqueToken>();
        for (final var allowance : nftAllowances) {
            final var serialNums = allowance.getSerialNumbersList();
            final var tokenId = Id.fromGrpcToken(allowance.getTokenId());
            final var owner = fetchOwnerAccount(allowance.getOwner(), payerAccount, mirrorState);
            final var token = mirrorState.loadPossiblyPausedToken(tokenId);
            for (final var serial : serialNums) {
                final var nft = mirrorState.loadUniqueToken(tokenId, serial);
                nft.clearSpender();
                nfts.add(nft);
            }
            nftsTouched.addAll(nfts);
            nfts.clear();
        }
    }
}
