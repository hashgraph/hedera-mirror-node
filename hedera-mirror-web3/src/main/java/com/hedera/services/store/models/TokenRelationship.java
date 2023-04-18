/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.tokens.TokenType;

/**
 * Encapsulates the state and operations of a Hedera account-token relationship.
 *
 * <p>Operations are validated, and throw a {@link InvalidTransactionException} with response code
 * capturing the failure when one occurs.
 *
 * <p><b>NOTE:</b> Some operations will likely be moved to specializations of this class as NFTs are
 * fully supported. For example, a {@link TokenRelationship#getBalanceChange()} signature only makes sense for a token
 * of type {@code FUNGIBLE_COMMON}; the analogous signature for a {@code NON_FUNGIBLE_UNIQUE} is
 * {@code getOwnershipChanges())}, returning a type that is structurally equivalent to a {@code Pair<long[], long[]>} of
 * acquired and relinquished serial numbers.
 */
public class TokenRelationship {
    private final Token token;
    private final Account account;
    private final long balance;
    private final boolean frozen;
    private final boolean kycGranted;
    private final boolean destroyed;
    private final boolean notYetPersisted;
    private final boolean automaticAssociation;

    private final long balanceChange;

    public TokenRelationship(Token token, Account account, long balance, boolean frozen, boolean kycGranted,
                             boolean destroyed, boolean notYetPersisted, boolean automaticAssociation,
                             long balanceChange) {
        this.token = token;
        this.account = account;
        this.balance = balance;
        this.frozen = frozen;
        this.kycGranted = kycGranted;
        this.destroyed = destroyed;
        this.notYetPersisted = notYetPersisted;
        this.automaticAssociation = automaticAssociation;
        this.balanceChange = balanceChange;
    }

    private TokenRelationship createCreateNewTokenRelationshipWithNewBalance(TokenRelationship tokenRel,
                                                                             long balanceChange, long balance) {
        return new TokenRelationship(tokenRel.token, tokenRel.account, balance,
                tokenRel.frozen, tokenRel.kycGranted, tokenRel.destroyed, tokenRel.notYetPersisted,
                tokenRel.automaticAssociation, balanceChange);
    }

    private TokenRelationship createCreateNewDestroyedTokenRelationship(TokenRelationship tokenRel) {
        return new TokenRelationship(tokenRel.token, tokenRel.account, tokenRel.balance,
                tokenRel.frozen, tokenRel.kycGranted, true, tokenRel.notYetPersisted,
                tokenRel.automaticAssociation, balanceChange);
    }

    private TokenRelationship createCreateNewPersistedTokenRelationship(TokenRelationship tokenRel) {
        return new TokenRelationship(tokenRel.token, tokenRel.account, tokenRel.balance,
                tokenRel.frozen, tokenRel.kycGranted, tokenRel.destroyed, false,
                tokenRel.automaticAssociation, balanceChange);
    }

    public long getBalance() {
        return balance;
    }

    /**
     * Update the balance of this relationship token held by the account.
     *
     * <p>This <b>does</b> change the return value of {@link TokenRelationship#getBalanceChange()}.
     *
     * @param balance the updated balance of the relationship
     */
    public TokenRelationship setBalance(long balance) {
        if (!token.isDeleted()) {
            validateTrue(isTokenFrozenFor(), ACCOUNT_FROZEN_FOR_TOKEN);
            validateTrue(isTokenKycGrantedFor(), ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN);
        }

        long newBalanceChange = (balance - this.balance) + balanceChange;
        return createCreateNewTokenRelationshipWithNewBalance(this, newBalanceChange, balance);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public boolean isKycGranted() {
        return kycGranted;
    }

    public long getBalanceChange() {
        return balanceChange;
    }

    public Token getToken() {
        return token;
    }

    public Account getAccount() {
        return account;
    }

    boolean hasInvolvedIds(Id tokenId, Id accountId) {
        return account.getId().equals(accountId) && token.getId().equals(tokenId);
    }

    public boolean isNotYetPersisted() {
        return notYetPersisted;
    }

    public TokenRelationship markAsPersisted() {
        return createCreateNewPersistedTokenRelationship(this);
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public TokenRelationship markAsDestroyed() {
        validateFalse(notYetPersisted, FAIL_INVALID);
        return createCreateNewDestroyedTokenRelationship(this);
    }

    public boolean hasChangesForRecord() {
        return balanceChange != 0 && (hasCommonRepresentation() || token.isDeleted());
    }

    public boolean hasCommonRepresentation() {
        return token.getType() == TokenType.FUNGIBLE_COMMON;
    }

    public boolean hasUniqueRepresentation() {
        return token.getType() == TokenType.NON_FUNGIBLE_UNIQUE;
    }

    public boolean isAutomaticAssociation() {
        return automaticAssociation;
    }

    private boolean isTokenFrozenFor() {
        return !token.hasFreezeKey() || !frozen;
    }

    private boolean isTokenKycGrantedFor() {
        return !token.hasKycKey() || kycGranted;
    }

    /* The object methods below are only overridden to improve
    readability of unit tests; model objects are not used in hash-based
    collections, so the performance of these methods doesn't matter. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(TokenRelationship.class)) {
            return false;
        }

        final var that = (TokenRelationship) obj;
        return new EqualsBuilder()
                .append(notYetPersisted, that.notYetPersisted)
                .append(account, that.account)
                .append(balance, that.balance)
                .append(balanceChange, that.balanceChange)
                .append(frozen, that.frozen)
                .append(kycGranted, that.kycGranted)
                .append(automaticAssociation, that.automaticAssociation)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(TokenRelationship.class)
                .add("notYetPersisted", notYetPersisted)
                .add("account", account)
                .add("token", token)
                .add("balance", balance)
                .add("balanceChange", balanceChange)
                .add("frozen", frozen)
                .add("kycGranted", kycGranted)
                .add("isAutomaticAssociation", automaticAssociation)
                .toString();
    }
}
