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

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.updateSpender;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 *  Copied Logic type from hedera-services. Differences with the original:
 *  1. Use abstraction for the state by introducing {@link Store} interface
 *  2. Remove AccountStore, TypedTokenStore and GlobalDynamicProperties
 *  3. Remove validateAllowanceLimitsOn since mirror-node properties do not have allowance limit
 *  4. Using exception from {@link com.hedera.mirror.web3.evm.store.StoreImpl} when entity is missing from the state
 */
public class ApproveAllowanceLogic {
    private final Store store;
    private final Map<Long, Account> accountsChanged;
    private final Map<NftId, UniqueToken> nftsTouched;

    public ApproveAllowanceLogic(final Store store) {
        this.store = store;
        this.accountsChanged = new TreeMap<>();
        this.nftsTouched = new TreeMap<>();
    }

    public void approveAllowance(
            final List<CryptoAllowance> cryptoAllowances,
            final List<TokenAllowance> tokenAllowances,
            final List<NftAllowance> nftAllowances,
            final AccountID payer) {
        accountsChanged.clear();
        nftsTouched.clear();

        /* --- Use models --- */
        final Id payerId = Id.fromGrpcAccount(payer);
        final var payerAccount = store.getAccount(payerId.asEvmAddress(), OnMissing.THROW);

        /* --- Do the business logic --- */
        applyCryptoAllowances(cryptoAllowances, payerAccount);
        applyFungibleTokenAllowances(tokenAllowances, payerAccount);
        applyNftAllowances(nftAllowances, payerAccount);

        /* --- Persist the entities --- */
        for (final var nft : nftsTouched.values()) {
            store.updateUniqueToken(nft);
        }
        for (final var account : accountsChanged.entrySet()) {
            store.updateAccount(account.getValue());
        }
    }

    /**
     * Applies all changes needed for Crypto allowances from the transaction. If the spender already
     * has an allowance, the allowance value will be replaced with values from transaction
     *
     * @param cryptoAllowances
     * @param payerAccount
     */
    private void applyCryptoAllowances(final List<CryptoAllowance> cryptoAllowances, Account payerAccount) {
        if (cryptoAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : cryptoAllowances) {
            final var owner = allowance.getOwner();
            var accountToApprove = fetchOwnerAccount(owner, payerAccount, store, accountsChanged);
            final var cryptoMap = accountToApprove.getCryptoAllowances();

            final var spender = Id.fromGrpcAccount(allowance.getSpender());
            final var amount = allowance.getAmount();
            if (cryptoMap.containsKey(spender.asEntityNum()) && amount == 0) {
                // spender need not be validated as being a valid account when removing allowances,
                // since it might be deleted and allowance is being removed by owner if it exists in map.
                removeEntity(cryptoMap, spender, accountToApprove);
            }
            if (amount > 0) {
                // To add allowances spender should be validated as being a valid account
                store.getAccount(spender.asEvmAddress(), OnMissing.THROW);

                cryptoMap.put(spender.asEntityNum(), amount);
                accountToApprove = accountToApprove.setCryptoAllowances(cryptoMap);
                accountsChanged.put(accountToApprove.getId().num(), accountToApprove);
            }
        }
    }

    private void removeEntity(final SortedMap<EntityNum, Long> cryptoMap, final Id spender, Account accountToApprove) {
        cryptoMap.remove(spender.asEntityNum());
        accountToApprove = accountToApprove.setCryptoAllowances(cryptoMap);
        accountsChanged.put(accountToApprove.getId().num(), accountToApprove);
    }

    /**
     * Applies all changes needed for fungible token allowances from the transaction.If the key
     * {token, spender} already has an allowance, the allowance value will be replaced with values
     * from transaction
     *
     * @param tokenAllowances
     * @param payerAccount
     */
    private void applyFungibleTokenAllowances(final List<TokenAllowance> tokenAllowances, Account payerAccount) {
        if (tokenAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : tokenAllowances) {
            final var owner = allowance.getOwner();
            var accountToApprove = fetchOwnerAccount(owner, payerAccount, store, accountsChanged);
            final var tokensMap = accountToApprove.getFungibleTokenAllowances();

            final var spender = Id.fromGrpcAccount(allowance.getSpender());
            final var amount = allowance.getAmount();
            final var tokenId = allowance.getTokenId();

            final var key = FcTokenAllowanceId.from(EntityNum.fromTokenId(tokenId), spender.asEntityNum());
            if (tokensMap.containsKey(key) && amount == 0) {
                // spender need not be validated as being a valid account when removing allowances,
                // since it might be deleted and allowance is being removed by owner if it exists in map.
                removeTokenEntity(key, tokensMap, accountToApprove);
            }
            if (amount > 0) {
                // To add allowances spender should be validated as being a valid account
                store.getAccount(spender.asEvmAddress(), OnMissing.THROW);

                tokensMap.put(key, amount);
                accountToApprove = accountToApprove.setFungibleTokenAllowances(tokensMap);
                accountsChanged.put(accountToApprove.getId().num(), accountToApprove);
            }
        }
    }

    /**
     * Applies all changes needed for NFT allowances from the transaction. If the key{tokenNum,
     * spenderNum} doesn't exist in the map the allowance will be inserted. If the key exists,
     * existing allowance values will be replaced with new allowances given in operation
     *
     * @param nftAllowances
     * @param payerAccount
     */
    protected void applyNftAllowances(final List<NftAllowance> nftAllowances, final Account payerAccount) {
        if (nftAllowances.isEmpty()) {
            return;
        }
        for (final var allowance : nftAllowances) {
            final var owner = allowance.getOwner();
            var approvingAccount = fetchOwnerAccount(owner, payerAccount, store, accountsChanged);
            final var spenderId = Id.fromGrpcAccount(allowance.getSpender());
            final var tokenId = Id.fromGrpcToken(allowance.getTokenId());

            if (allowance.hasApprovedForAll()) {
                final var approveForAllNfts = approvingAccount.getApproveForAllNfts();
                final var key = FcTokenAllowanceId.from(tokenId.asEntityNum(), spenderId.asEntityNum());
                if (allowance.getApprovedForAll().getValue()) {
                    // Validate the spender/operator account
                    store.getAccount(spenderId.asEvmAddress(), OnMissing.THROW);
                    approveForAllNfts.add(key);
                } else {
                    // Need not validate anything here to revoke the approval
                    approveForAllNfts.remove(key);
                }
                approvingAccount = approvingAccount.setApproveForAllNfts(approveForAllNfts);
            }

            if (allowance.getSerialNumbersCount() > 0) {
                // To add allowance for any serials, need to validate spender
                store.getAccount(spenderId.asEvmAddress(), OnMissing.THROW);
            }

            final var nfts = updateSpender(
                    store, approvingAccount.getId(), spenderId, tokenId, allowance.getSerialNumbersList());
            for (final var nft : nfts) {
                nftsTouched.put(nft.getNftId(), nft);
            }
            accountsChanged.put(approvingAccount.getId().num(), approvingAccount);
        }
    }

    private void removeTokenEntity(
            final FcTokenAllowanceId key,
            final SortedMap<FcTokenAllowanceId, Long> tokensMap,
            Account accountToApprove) {
        tokensMap.remove(key);
        accountToApprove = accountToApprove.setFungibleTokenAllowances(tokensMap);
        accountsChanged.put(accountToApprove.getId().num(), accountToApprove);
    }
}
