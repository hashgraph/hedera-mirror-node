package com.hedera.services.store.models;

/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.services.utils.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.getMaxAutomaticAssociationsFrom;

import com.google.common.base.MoreObjects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.utils.EntityNum;

public class Account extends HederaEvmAccount {
    final Id id;
    final long expiry;
    final long balance;
    final boolean deleted = false;
    final boolean isSmartContract = false;
    final boolean isReceiverSigRequired = false;
    final long ownedNfts;
    final long autoRenewSecs;
    final Id proxy;
    final Address accountAddress;
    final int autoAssociationMetadata;
    final TreeMap<EntityNum, Long> cryptoAllowances;
    final TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances;
    final TreeSet<FcTokenAllowanceId> approveForAllNfts;
    final int numAssociations;
    final int numPositiveBalances;
    final int numTreasuryTitles;

    public Account(Id id, long expiry, long balance, long ownedNfts, long autoRenewSecs,
                   Id proxy, int autoAssociationMetadata,
                   TreeMap<EntityNum, Long> cryptoAllowances, TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances,
                   TreeSet<FcTokenAllowanceId> approveForAllNfts, int numAssociations, int numPositiveBalances,
                   int numTreasuryTitles) {
        super(id.asEvmAddress());
        this.id = id;
        this.expiry = expiry;
        this.balance = balance;
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

    public int getMaxAutomaticAssociations() {
        return getMaxAutomaticAssociationsFrom(autoAssociationMetadata);
    }

    public int getAlreadyUsedAutomaticAssociations() {
        return getAlreadyUsedAutomaticAssociationsFrom(autoAssociationMetadata);
    }

    /* NOTE: The object methods below are only overridden to improve
    readability of unit tests; this model object is not used in hash-based
    collections, so the performance of these methods doesn't matter. */

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
