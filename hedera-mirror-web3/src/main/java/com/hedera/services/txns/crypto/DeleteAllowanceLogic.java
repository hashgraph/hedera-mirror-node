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
import static com.hedera.services.store.models.Id.fromGrpcAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.validOwner;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import java.util.ArrayList;
import java.util.List;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Remove AccountStore and TypedTokenStore
 *  3. Using exception from {@link com.hedera.mirror.web3.evm.store.StoreImpl} when entity is missing from the state
 */
public class DeleteAllowanceLogic {
    private final Store store;
    private final List<UniqueToken> nftsTouched;

    public DeleteAllowanceLogic(final Store store) {
        this.store = store;
        this.nftsTouched = new ArrayList<>();
    }

    public void deleteAllowance(final List<NftRemoveAllowance> nftAllowancesList, final AccountID payer) {
        nftsTouched.clear();

        // --- Load models ---
        final Id payerId = fromGrpcAccount(payer);
        final var payerAccount = store.getAccount(payerId.asEvmAddress(), OnMissing.THROW);
        // --- Do the business logic ---
        deleteNftSerials(nftAllowancesList, payerAccount);

        // --- Persist the owner accounts and nfts ---
        for (final var nft : nftsTouched) {
            store.updateUniqueToken(nft);
        }
    }

    /**
     * Clear spender on the provided nft serials. If the owner is not provided in any allowance,
     * considers payer of the transaction as owner while checking if nft is owned by owner.
     *
     * @param nftAllowances given nftAllowances
     * @param payerAccount payer for the transaction
     */
    private void deleteNftSerials(final List<NftRemoveAllowance> nftAllowances, final Account payerAccount) {
        if (nftAllowances.isEmpty()) {
            return;
        }
        final var nfts = new ArrayList<UniqueToken>();
        for (final var allowance : nftAllowances) {
            final var serialNums = allowance.getSerialNumbersList();
            final var tokenId = Id.fromGrpcToken(allowance.getTokenId());
            final var owner = fetchOwnerAccount(allowance.getOwner(), payerAccount, store);
            final var token = store.getToken(tokenId.asEvmAddress(), OnMissing.THROW);
            for (final var serial : serialNums) {
                final var nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serial);
                final var nft = store.getUniqueToken(nftId, OnMissing.THROW);
                validateTrue(validOwner(nft, owner.getId(), token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
                nfts.add(nft.setSpender(Id.DEFAULT));
            }
            nftsTouched.addAll(nfts);
            nfts.clear();
        }
    }
}
