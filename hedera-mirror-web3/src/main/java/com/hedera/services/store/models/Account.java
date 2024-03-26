/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.utils.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import lombok.Getter;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Account model from hedera-services.
 * <p>
 * This model is used as a value in a special state (CachingStateFrame), used for speculative write operations. Object
 * immutability is required for this model in order to be used seamlessly in the state.
 * <p>
 * Differences with the original:
 * 1. Removed fields like memo, key, isReceiverSigRequired, isSmartContract
 * 2. Added field accountAddress for convenience
 * 3. Changed collection types to SortedMap and SortedSet
 * 4. Added constructors and set methods for creating new instances and achieve immutability
 * 6. Added factory method that returns empty instance
 * 7. Added isEmptyAccount() method
 */
@Getter
public class Account extends HederaEvmAccount {
    private final Long entityId;

    private final Id id;

    private final long expiry;

    private final Supplier<Long> balance;

    private final boolean deleted;

    private final Supplier<Long> ownedNfts;

    private final long autoRenewSecs;

    private final Id proxy;

    private final Address accountAddress;

    private final int autoAssociationMetadata;

    private final Supplier<SortedMap<EntityNum, Long>> cryptoAllowances;

    private final Supplier<SortedMap<FcTokenAllowanceId, Long>> fungibleTokenAllowances;

    private final Supplier<SortedSet<FcTokenAllowanceId>> approveForAllNfts;

    private final Supplier<Integer> numAssociations;

    private final Supplier<Integer> numPositiveBalances;

    private final int numTreasuryTitles;

    private final long ethereumNonce;

    private final boolean isSmartContract;

    private final JKey key;

    private final long createdTimestamp;

    @SuppressWarnings("java:S107")
    public Account(
            ByteString alias,
            Long entityId,
            Id id,
            long expiry,
            Supplier<Long> balance,
            boolean deleted,
            Supplier<Long> ownedNfts,
            long autoRenewSecs,
            Id proxy,
            int autoAssociationMetadata,
            Supplier<SortedMap<EntityNum, Long>> cryptoAllowances,
            Supplier<SortedMap<FcTokenAllowanceId, Long>> fungibleTokenAllowances,
            Supplier<SortedSet<FcTokenAllowanceId>> approveForAllNfts,
            Supplier<Integer> numAssociations,
            Supplier<Integer> numPositiveBalances,
            int numTreasuryTitles,
            long ethereumNonce,
            boolean isSmartContract,
            JKey key,
            long createdTimestamp) {
        super(id.asEvmAddress());
        setAlias(alias);
        this.entityId = entityId;
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
        this.ethereumNonce = ethereumNonce;
        this.isSmartContract = isSmartContract;
        this.key = key;
        this.createdTimestamp = createdTimestamp;
    }

    /**
     * Create a partial account with only ID and balance values.
     * Used for treasury accounts as those are the only fields we need.
     */
    public Account(Long entityId, Id id, long balance) {
        this(
                ByteString.EMPTY,
                entityId,
                id,
                0L,
                () -> balance,
                false,
                () -> 0L,
                0L,
                null,
                0,
                Collections::emptySortedMap,
                Collections::emptySortedMap,
                Collections::emptySortedSet,
                () -> 0,
                () -> 0,
                0,
                0L,
                false,
                null,
                0L);
    }

    /**
     * Create a partial account with only alias, ID and balance values.
     * Used for treasury accounts as those are the only fields we need.
     */
    public Account(ByteString alias, Long entityId, Id id, long balance) {
        this(
                alias,
                entityId,
                id,
                0L,
                () -> balance,
                false,
                () -> 0L,
                0L,
                null,
                0,
                Collections::emptySortedMap,
                Collections::emptySortedMap,
                Collections::emptySortedSet,
                () -> 0,
                () -> 0,
                0,
                0L,
                false,
                null,
                0L);
    }

    public static Account getEmptyAccount() {
        return new Account(0L, Id.DEFAULT, 0L);
    }

    public static Account getDummySenderAccount(Address senderAddress) {
        return new Account(
                0L, Id.fromGrpcAccount(EntityIdUtils.accountIdFromEvmAddress(senderAddress)), Long.MAX_VALUE);
    }

    public boolean isEmptyAccount() {
        return this.equals(getEmptyAccount());
    }

    /**
     * Creates new instance of {@link Account} with updated ownedNfts in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param ownedNfts
     * @return the new instance of {@link Account} with updated {@link #ownedNfts} property
     */
    private Account createNewAccountWithNewOwnedNfts(final Account oldAccount, final long ownedNfts) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
                oldAccount.id,
                oldAccount.expiry,
                oldAccount.balance,
                oldAccount.deleted,
                () -> ownedNfts,
                oldAccount.autoRenewSecs,
                oldAccount.proxy,
                oldAccount.autoAssociationMetadata,
                oldAccount.cryptoAllowances,
                oldAccount.fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated numAssociations in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param numAssociations
     * @return the new instance of {@link Account} with updated {@link #numAssociations} property
     */
    private Account createNewAccountWithNumAssociations(final Account oldAccount, final int numAssociations) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
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
                () -> numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated numPositiveBalances in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param newNumPositiveBalances
     * @return the new instance of {@link Account} with updated {@link #numPositiveBalances} property
     */
    private Account createNewAccountWithNewPositiveBalances(Account oldAccount, int newNumPositiveBalances) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
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
                () -> newNumPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated autoAssociationMetadata in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param updatedAutoAssociationMetadata
     * @return the new instance of {@link Account} with updated {@link #autoAssociationMetadata} property
     */
    private Account createNewAccountWithNewAutoAssociationMetadata(
            Account oldAccount, int updatedAutoAssociationMetadata) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
                oldAccount.id,
                oldAccount.expiry,
                oldAccount.balance,
                oldAccount.deleted,
                oldAccount.ownedNfts,
                oldAccount.autoRenewSecs,
                oldAccount.proxy,
                updatedAutoAssociationMetadata,
                oldAccount.cryptoAllowances,
                oldAccount.fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated isSmartContract in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param isSmartContract
     * @return the new instance of {@link Account} with updated {@link #isSmartContract} property
     */
    private Account createNewAccountWithNewIsSmartContract(Account oldAccount, boolean isSmartContract) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
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
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated expiry in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param expiry
     * @return the new instance of {@link Account} with updated {@link #expiry} property
     */
    private Account createNewAccountWithNewExpiry(Account oldAccount, long expiry) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
                oldAccount.id,
                expiry,
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
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated balance in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param newBalance
     * @return the new instance of {@link Account} with updated {@link #balance} property
     */
    private Account createNewAccountWithNewBalance(Account oldAccount, long newBalance) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
                oldAccount.id,
                oldAccount.expiry,
                () -> newBalance,
                oldAccount.deleted,
                oldAccount.ownedNfts,
                oldAccount.autoRenewSecs,
                oldAccount.proxy,
                oldAccount.autoAssociationMetadata,
                oldAccount.cryptoAllowances,
                oldAccount.fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated cryptoAllowances in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param cryptoAllowances
     * @return the new instance of {@link Account} with updated {@link #cryptoAllowances} property
     */
    private Account createNewAccountWithNewCryptoAllowances(
            Account oldAccount, SortedMap<EntityNum, Long> cryptoAllowances) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
                oldAccount.id,
                oldAccount.expiry,
                oldAccount.balance,
                oldAccount.deleted,
                oldAccount.ownedNfts,
                oldAccount.autoRenewSecs,
                oldAccount.proxy,
                oldAccount.autoAssociationMetadata,
                () -> cryptoAllowances,
                oldAccount.fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated fungibleTokenAllowances in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param fungibleTokenAllowances
     * @return the new instance of {@link Account} with updated {@link #fungibleTokenAllowances} property
     */
    private Account createNewAccountWithNewFungibleTokenAllowances(
            Account oldAccount, SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
                oldAccount.id,
                oldAccount.expiry,
                oldAccount.balance,
                oldAccount.deleted,
                oldAccount.ownedNfts,
                oldAccount.autoRenewSecs,
                oldAccount.proxy,
                oldAccount.autoAssociationMetadata,
                oldAccount.cryptoAllowances,
                () -> fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated approval for all nfts in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param newApproveForAllNfts
     * @return the new instance of {@link Account} with updated {@link #approveForAllNfts} property
     */
    private Account createNewAccountWithNewApproveForAllNfts(
            Account oldAccount, SortedSet<FcTokenAllowanceId> newApproveForAllNfts) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
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
                () -> newApproveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated numTreasuryTitles in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param newNumTreasuryTitles
     * @return the new instance of {@link Account} with updated {@link #approveForAllNfts} property
     */
    private Account createNewAccountWithNewNumTreasuryTitles(Account oldAccount, int newNumTreasuryTitles) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
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
                oldAccount.numPositiveBalances,
                newNumTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    /**
     * Creates new instance of {@link Account} with updated deleted in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldAccount
     * @param newDeleted
     * @return the new instance of {@link Account} with updated {@link #deleted} property
     */
    private Account createNewAccountWithNewDeleted(Account oldAccount, boolean newDeleted) {
        return new Account(
                oldAccount.alias,
                oldAccount.entityId,
                oldAccount.id,
                oldAccount.expiry,
                oldAccount.balance,
                newDeleted,
                oldAccount.ownedNfts,
                oldAccount.autoRenewSecs,
                oldAccount.proxy,
                oldAccount.autoAssociationMetadata,
                oldAccount.cryptoAllowances,
                oldAccount.fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key,
                oldAccount.createdTimestamp);
    }

    public int getMaxAutomaticAssociations() {
        return getMaxAutomaticAssociationsFrom(autoAssociationMetadata);
    }

    public int getAlreadyUsedAutomaticAssociations() {
        return getAlreadyUsedAutomaticAssociationsFrom(autoAssociationMetadata);
    }

    public Account setAlreadyUsedAutomaticAssociations(int alreadyUsedCount) {
        validateTrue(isValidAlreadyUsedCount(alreadyUsedCount), NO_REMAINING_AUTOMATIC_ASSOCIATIONS);
        final var updatedAutoAssociationMetadata =
                setAlreadyUsedAutomaticAssociationsTo(autoAssociationMetadata, alreadyUsedCount);
        return createNewAccountWithNewAutoAssociationMetadata(this, updatedAutoAssociationMetadata);
    }

    public Account setAutoAssociationMetadata(int newAutoAssociationMetadata) {
        return createNewAccountWithNewAutoAssociationMetadata(this, newAutoAssociationMetadata);
    }

    public Account setExpiry(long expiry) {
        return createNewAccountWithNewExpiry(this, expiry);
    }

    public Account setBalance(long balance) {
        return createNewAccountWithNewBalance(this, balance);
    }

    public Account setDeleted(boolean deleted) {
        return createNewAccountWithNewDeleted(this, deleted);
    }

    public boolean isSmartContract() {
        return isSmartContract;
    }

    public Account setIsSmartContract(boolean isSmartContract) {
        return createNewAccountWithNewIsSmartContract(this, isSmartContract);
    }

    public Account setCryptoAllowance(SortedMap<EntityNum, Long> cryptoAllowances) {
        return createNewAccountWithNewCryptoAllowances(this, cryptoAllowances);
    }

    public Account setOwnedNfts(long newOwnedNfts) {
        return createNewAccountWithNewOwnedNfts(this, newOwnedNfts);
    }

    public Account setFungibleTokenAllowances(SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
        return createNewAccountWithNewFungibleTokenAllowances(this, fungibleTokenAllowances);
    }

    public Account setApproveForAllNfts(SortedSet<FcTokenAllowanceId> approveForAllNfts) {
        return createNewAccountWithNewApproveForAllNfts(this, approveForAllNfts);
    }

    public Account setNumAssociations(int numAssociations) {
        return createNewAccountWithNumAssociations(this, numAssociations);
    }

    public Account setNumTreasuryTitles(int numTreasuryTitles) {
        return createNewAccountWithNewNumTreasuryTitles(this, numTreasuryTitles);
    }

    public Account setNumPositiveBalances(int newNumPositiveBalances) {
        return createNewAccountWithNewPositiveBalances(this, newNumPositiveBalances);
    }

    public Account decrementUsedAutomaticAssociations() {
        var count = getAlreadyUsedAutomaticAssociations();
        return setAlreadyUsedAutomaticAssociations(--count);
    }

    public Long getBalance() {
        return balance != null && balance.get() != null ? balance.get() : 0L;
    }

    public Long getOwnedNfts() {
        return ownedNfts != null ? ownedNfts.get() : 0L;
    }

    public SortedMap<EntityNum, Long> getCryptoAllowances() {
        return Collections.unmodifiableSortedMap(cryptoAllowances != null ? cryptoAllowances.get() : new TreeMap<>());
    }

    public SortedMap<FcTokenAllowanceId, Long> getFungibleTokenAllowances() {
        return Collections.unmodifiableSortedMap(
                fungibleTokenAllowances != null ? fungibleTokenAllowances.get() : new TreeMap<>());
    }

    public SortedSet<FcTokenAllowanceId> getApproveForAllNfts() {
        return Collections.unmodifiableSortedSet(approveForAllNfts != null ? approveForAllNfts.get() : new TreeSet<>());
    }

    public Integer getNumAssociations() {
        return numAssociations != null ? numAssociations.get() : 0;
    }

    public Integer getNumPositiveBalances() {
        return numPositiveBalances != null ? numPositiveBalances.get() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return getExpiry() == account.getExpiry()
                && isDeleted() == account.isDeleted()
                && getAutoRenewSecs() == account.getAutoRenewSecs()
                && getAutoAssociationMetadata() == account.getAutoAssociationMetadata()
                && getNumTreasuryTitles() == account.getNumTreasuryTitles()
                && getEthereumNonce() == account.getEthereumNonce()
                && isSmartContract() == account.isSmartContract()
                && getCreatedTimestamp() == account.getCreatedTimestamp()
                && Objects.equals(getEntityId(), account.getEntityId())
                && Objects.equals(getId(), account.getId())
                && Objects.equals(getBalance(), account.getBalance())
                && Objects.equals(getOwnedNfts(), account.getOwnedNfts())
                && Objects.equals(getProxy(), account.getProxy())
                && Objects.equals(getAccountAddress(), account.getAccountAddress())
                && getCryptoAllowances().equals(account.getCryptoAllowances())
                && getFungibleTokenAllowances().equals(account.getFungibleTokenAllowances())
                && getApproveForAllNfts().equals(account.getApproveForAllNfts())
                && Objects.equals(getNumAssociations(), account.getNumAssociations())
                && Objects.equals(getNumPositiveBalances(), account.getNumPositiveBalances())
                && Objects.equals(getKey(), account.getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getAlias(),
                getEntityId(),
                getId(),
                getExpiry(),
                getBalance(),
                isDeleted(),
                getOwnedNfts(),
                getAutoRenewSecs(),
                getProxy(),
                getAccountAddress(),
                getAutoAssociationMetadata(),
                getCryptoAllowances(),
                getFungibleTokenAllowances(),
                getApproveForAllNfts(),
                getNumAssociations(),
                getNumPositiveBalances(),
                getNumTreasuryTitles(),
                getEthereumNonce(),
                isSmartContract(),
                getKey(),
                getCreatedTimestamp());
    }

    @Override
    public String toString() {
        return "Account{" + "entityId="
                + entityId + ", id="
                + id + ", alias="
                + alias.toStringUtf8() + ", address="
                + address + ", expiry="
                + expiry + ", balance="
                + getBalance() + ", deleted="
                + deleted + ", ownedNfts="
                + getOwnedNfts() + ", autoRenewSecs="
                + autoRenewSecs + ", proxy="
                + proxy + ", accountAddress="
                + accountAddress + ", autoAssociationMetadata="
                + autoAssociationMetadata + ", cryptoAllowances="
                + getCryptoAllowances() + ", fungibleTokenAllowances="
                + getFungibleTokenAllowances() + ", approveForAllNfts="
                + getApproveForAllNfts() + ", numAssociations="
                + getNumAssociations() + ", numPositiveBalances="
                + getNumPositiveBalances() + ", numTreasuryTitles="
                + numTreasuryTitles + ", ethereumNonce="
                + ethereumNonce + ", isSmartContract="
                + isSmartContract + ", key="
                + key + ", createdTimestamp="
                + createdTimestamp + "}";
    }

    private boolean isValidAlreadyUsedCount(int alreadyUsedCount) {
        return alreadyUsedCount >= 0 && alreadyUsedCount <= getMaxAutomaticAssociations();
    }
}
