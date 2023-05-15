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

package com.hedera.services.store.models;

import static com.hedera.services.utils.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.getMaxAutomaticAssociationsFrom;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.utils.EntityNum;
import java.util.SortedMap;
import java.util.SortedSet;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hyperledger.besu.datatypes.Address;

/**
 *
 * This model is used as a value in a special state (CachingStateFrame), used for speculative write operations. Object
 * immutability is required for this model in order to be used seamlessly in the state.
 */
public class Account extends HederaEvmAccount {
    private final Id id;
    private final long expiry;
    private final long balance;
    private final boolean deleted;
    private final long ownedNfts;

    private final long autoRenewSecs;
    final Id proxy;
    private final Address accountAddress;
    private final int autoAssociationMetadata;
    private final SortedMap<EntityNum, Long> cryptoAllowances;
    private final SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances;
    private final SortedSet<FcTokenAllowanceId> approveForAllNfts;
    private final int numAssociations;
    private final int numPositiveBalances;
    private final int numTreasuryTitles;

    @SuppressWarnings("java:S107")
    public Account(
            Id id,
            long expiry,
            long balance,
            boolean deleted,
            long ownedNfts,
            long autoRenewSecs,
            Id proxy,
            int autoAssociationMetadata,
            SortedMap<EntityNum, Long> cryptoAllowances,
            SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances,
            SortedSet<FcTokenAllowanceId> approveForAllNfts,
            int numAssociations,
            int numPositiveBalances,
            int numTreasuryTitles) {
        super(id.asEvmAddress());
        this.id = id;
        this.expiry = expiry;
        this.balance = balance;
        this.deleted = deleted;
        this.ownedNfts = ownedNfts;
        this.autoRenewSecs = autoRenewSecs;
        this.proxy = proxy;
        this.accountAddress = id.asEvmAddress();
        this.autoAssociationMetadata = autoAssociationMetadata;
        this.cryptoAllowances = cryptoAllowances;
        this.fungibleTokenAllowances = fungibleTokenAllowances;
        this.approveForAllNfts = approveForAllNfts;
        this.numAssociations = numAssociations;
        this.numPositiveBalances = numPositiveBalances;
        this.numTreasuryTitles = numTreasuryTitles;
    }

    /**
     * Creates new instance of {@link Account} with updated ownedNfts in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param ownedNfts
     * @return the new instance of {@link Account} with updated {@link #ownedNfts} property
     */
    private Account createNewAccountWithNewOwnedNfts(Account oldAccount, long ownedNfts) {
        return new Account(
                oldAccount.id,
                oldAccount.expiry,
                oldAccount.balance,
                oldAccount.deleted,
                ownedNfts,
                oldAccount.autoRenewSecs,
                oldAccount.proxy,
                oldAccount.autoAssociationMetadata,
                oldAccount.cryptoAllowances,
                oldAccount.fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles);
    }

    /**
     *
     * Creates new instance of {@link Account} with updated numPositiveBalances in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param newNumPositiveBalances
     * @return the new instance of {@link Account} with updated {@link #numPositiveBalances} property
     */
    private Account createNewAccountWithNewPositiveBalances(Account oldAccount, int newNumPositiveBalances) {
        return new Account(
                oldAccount.id,
                oldAccount.expiry,
                oldAccount.balance,
                oldAccount.deleted,
                oldAccount.ownedNfts,
                oldAccount.autoRenewSecs,
                oldAccount.proxy,
                oldAccount.autoAssociationMetadata,
                oldAccount.cryptoAllowances,
                oldAccount.fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                newNumPositiveBalances,
                oldAccount.numTreasuryTitles);
    }

    public int getMaxAutomaticAssociations() {
        return getMaxAutomaticAssociationsFrom(autoAssociationMetadata);
    }

    public int getAlreadyUsedAutomaticAssociations() {
        return getAlreadyUsedAutomaticAssociationsFrom(autoAssociationMetadata);
    }

    public Id getId() {
        return id;
    }

    public long getExpiry() {
        return expiry;
    }

    public long getBalance() {
        return balance;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getOwnedNfts() {
        return ownedNfts;
    }

    public Account setOwnedNfts(long newOwnedNfts) {
        return createNewAccountWithNewOwnedNfts(this, newOwnedNfts);
    }

    public long getAutoRenewSecs() {
        return autoRenewSecs;
    }

    public Id getProxy() {
        return proxy;
    }

    public Address getAccountAddress() {
        return accountAddress;
    }

    public SortedMap<EntityNum, Long> getCryptoAllowances() {
        return cryptoAllowances;
    }

    public SortedMap<FcTokenAllowanceId, Long> getFungibleTokenAllowances() {
        return fungibleTokenAllowances;
    }

    public SortedSet<FcTokenAllowanceId> getApproveForAllNfts() {
        return approveForAllNfts;
    }

    public int getNumAssociations() {
        return numAssociations;
    }

    public int getNumTreasuryTitles() {
        return numTreasuryTitles;
    }

    public int getNumPositiveBalances() {
        return numPositiveBalances;
    }

    public Account setNumPositiveBalances(int newNumPositiveBalances) {
        return createNewAccountWithNewPositiveBalances(this, newNumPositiveBalances);
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(Account.class)
                .add("id", id)
                .add("expiry", expiry)
                .add("balance", balance)
                .add("deleted", deleted)
                .add("ownedNfts", ownedNfts)
                .add("alreadyUsedAutoAssociations", getAlreadyUsedAutomaticAssociations())
                .add("maxAutoAssociations", getMaxAutomaticAssociations())
                .add("alias", getAlias().toStringUtf8())
                .add("cryptoAllowances", cryptoAllowances)
                .add("fungibleTokenAllowances", fungibleTokenAllowances)
                .add("approveForAllNfts", approveForAllNfts)
                .add("numAssociations", numAssociations)
                .add("numPositiveBalances", numPositiveBalances)
                .toString();
    }
}
