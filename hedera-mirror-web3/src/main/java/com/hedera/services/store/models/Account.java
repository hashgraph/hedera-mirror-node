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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.utils.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.utils.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.utils.EntityNum;

@Value
@Builder
public class Account extends HederaEvmAccount {
    Id id;
    long expiry;
    long balance;
    boolean deleted = false;
    boolean isSmartContract = false;
    boolean isReceiverSigRequired = false;
    long ownedNfts;
    long autoRenewSecs;
    String memo = "";
    Id proxy;
    Address accountAddress;
    int autoAssociationMetadata;
    TreeMap<EntityNum, Long> cryptoAllowances;
    TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances;
    TreeSet<FcTokenAllowanceId> approveForAllNfts;
    int numAssociations;
    int numPositiveBalances;
    int numTreasuryTitles;

    public Account(Id id, long expiry, long balance, long ownedNfts, long autoRenewSecs,
                   Id proxy, Address accountAddress, int autoAssociationMetadata,
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
        this.accountAddress = accountAddress;
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

    /**
     * Associated the given list of Tokens to this account.
     *
     * @param tokens                   List of tokens to be associated to the Account
     * @param tokenStore               TypedTokenStore to validate if existing relationship with the tokens to be
     *                                 associated with
     * @param isAutomaticAssociation   whether these associations count against the max auto-associations limit
     * @param shouldEnableRelationship whether the new relationships should be enabled unconditionally, no matter KYC
     *                                 and freeze settings
     * @param dynamicProperties        GlobalDynamicProperties to fetch the token associations limit and enforce it.
     * @return the new token relationships formed by this association
     */
    public List<TokenRelationship> associateWith(
            final List<Token> tokens,
            final TypedTokenStore tokenStore,
            final boolean isAutomaticAssociation,
            final boolean shouldEnableRelationship,
            final GlobalDynamicProperties dynamicProperties) {
        final var proposedTotalAssociations = tokens.size() + numAssociations;
        validateFalse(
                exceedsTokenAssociationLimit(dynamicProperties, proposedTotalAssociations),
                TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);
        final List<TokenRelationship> newModelRels = new ArrayList<>();
        for (final var token : tokens) {
            validateFalse(tokenStore.hasAssociation(token, this), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
            if (isAutomaticAssociation) {
                incrementUsedAutomaticAssociations();
            }
            final var newRel = shouldEnableRelationship
                    ? token.newEnabledRelationship(this)
                    : token.newRelationshipWith(this, false);
            numAssociations++;
            newModelRels.add(newRel);
        }
        return newModelRels;
    }

    public void dissociateUsing(final List<Dissociation> dissociations, final OptionValidator validator) {
        for (final var dissociation : dissociations) {
            validateTrue(id.equals(dissociation.dissociatingAccountId()), FAIL_INVALID);
            dissociation.updateModelRelsSubjectTo(validator);
            final var pastRel = dissociation.dissociatingAccountRel();
            if (pastRel.isAutomaticAssociation()) {
                decrementUsedAutomaticAssociations();
            }
            if (pastRel.getBalanceChange() != 0) {
                numPositiveBalances--;
            }
            numAssociations--;
        }
    }

    private boolean exceedsTokenAssociationLimit(MirrorNodeEvmProperties dynamicProperties, int totalAssociations) {
        return dynamicProperties.areTokenAssociationsLimited()
                && totalAssociations > dynamicProperties.maxTokensPerAccount();
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
