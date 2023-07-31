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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.utils.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.jproto.JKey;
import com.hedera.services.utils.EntityNum;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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
public class Account extends HederaEvmAccount {
    private final Long entityId;
    private final Id id;
    private final long expiry;
    private final long balance;
    private final boolean deleted;
    private final long ownedNfts;
    private final long autoRenewSecs;
    private final Id proxy;
    private final Address accountAddress;
    private final int autoAssociationMetadata;
    private final SortedMap<EntityNum, Long> cryptoAllowances;
    private final SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances;
    private final SortedSet<FcTokenAllowanceId> approveForAllNfts;
    private final int numAssociations;
    private final int numPositiveBalances;
    private final int numTreasuryTitles;
    private final long ethereumNonce;
    private final boolean isSmartContract;
    private final JKey key;

    @SuppressWarnings("java:S107")
    public Account(
            ByteString alias,
            Long entityId,
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
            int numTreasuryTitles,
            long ethereumNonce,
            boolean isSmartContract,
            JKey key) {
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
                balance,
                false,
                0L,
                0L,
                null,
                0,
                new TreeMap<>(),
                new TreeMap<>(),
                new TreeSet<>(),
                0,
                0,
                0,
                0L,
                false,
                null);
    }

    public static Account getEmptyAccount() {
        return new Account(0L, Id.DEFAULT, 0L);
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
                ownedNfts,
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
                oldAccount.key);
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
                numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key);
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
                newNumPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                key);
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
                oldAccount.key);
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
                oldAccount.key);
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
                oldAccount.key);
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
                newBalance,
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
                oldAccount.key);
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
                cryptoAllowances,
                oldAccount.fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key);
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
                fungibleTokenAllowances,
                oldAccount.approveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                key);
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
                newApproveForAllNfts,
                oldAccount.numAssociations,
                oldAccount.numPositiveBalances,
                oldAccount.numTreasuryTitles,
                oldAccount.ethereumNonce,
                oldAccount.isSmartContract,
                oldAccount.key);
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
                oldAccount.key);
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
                oldAccount.key);
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

    public int getAutoAssociationMetadata() {
        return autoAssociationMetadata;
    }

    public Account setAutoAssociationMetadata(int newAutoAssociationMetadata) {
        return createNewAccountWithNewAutoAssociationMetadata(this, newAutoAssociationMetadata);
    }

    public Long getEntityId() {
        return entityId;
    }

    public Id getId() {
        return id;
    }

    public long getExpiry() {
        return expiry;
    }

    public Account setExpiry(long expiry) {
        return createNewAccountWithNewExpiry(this, expiry);
    }

    public long getBalance() {
        return balance;
    }

    public Account setBalance(long balance) {
        return createNewAccountWithNewBalance(this, balance);
    }

    public boolean isDeleted() {
        return deleted;
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

    public Account setFungibleTokenAllowances(SortedMap<FcTokenAllowanceId, Long> fungibleTokenAllowances) {
        return createNewAccountWithNewFungibleTokenAllowances(this, fungibleTokenAllowances);
    }

    public SortedSet<FcTokenAllowanceId> getApproveForAllNfts() {
        return approveForAllNfts;
    }

    public Account setApproveForAllNfts(SortedSet<FcTokenAllowanceId> approveForAllNfts) {
        return createNewAccountWithNewApproveForAllNfts(this, approveForAllNfts);
    }

    public int getNumAssociations() {
        return numAssociations;
    }

    public Account setNumAssociations(int numAssociations) {
        return createNewAccountWithNumAssociations(this, numAssociations);
    }

    public int getNumTreasuryTitles() {
        return numTreasuryTitles;
    }

    public Account setNumTreasuryTitles(int numTreasuryTitles) {
        return createNewAccountWithNewNumTreasuryTitles(this, numTreasuryTitles);
    }

    public int getNumPositiveBalances() {
        return numPositiveBalances;
    }

    public Account setNumPositiveBalances(int newNumPositiveBalances) {
        return createNewAccountWithNewPositiveBalances(this, newNumPositiveBalances);
    }

    public long getEthereumNonce() {
        return ethereumNonce;
    }

    public Account decrementUsedAutomaticAssociations() {
        var count = getAlreadyUsedAutomaticAssociations();
        return setAlreadyUsedAutomaticAssociations(--count);
    }

    public JKey getKey() {
        return this.key;
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

    private boolean isValidAlreadyUsedCount(int alreadyUsedCount) {
        return alreadyUsedCount >= 0 && alreadyUsedCount <= getMaxAutomaticAssociations();
    }
}
