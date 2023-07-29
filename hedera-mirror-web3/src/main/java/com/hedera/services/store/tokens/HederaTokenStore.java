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

package com.hedera.services.store.tokens;

import static com.hedera.node.app.service.evm.store.tokens.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.utils.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.EntityIdUtils.toGrpcAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNEXPECTED_TOKEN_DECIMALS;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.exceptions.MissingEntityException;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Provides a managing store for arbitrary tokens.
 * Differences with the original:
 * <ol>
 * <li>Removed validations performed in UsageLimits, since they check global node limits,
 * while on Archive Node we are interested in transaction scope only</li>
 * <li>Removed SideEffectsTracker and EntityIdSource</li>
 * <li>Use abstraction for the state by introducing {@link Store} interface</li>
 * <li>Use Mirror Node specific properties - {@link MirrorNodeEvmProperties}</li>
 * <li>Copied `usabilityOf` from `HederaLedger`</li>
 * <li>Renamed `updateLedgers()` to `updateStore()`</li>
 * </ol>
 */
public class HederaTokenStore {

    static final TokenID NO_PENDING_ID = TokenID.getDefaultInstance();
    static final TokenID MISSING_TOKEN = TokenID.getDefaultInstance();
    private final ContextOptionValidator validator;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;
    private final Store store;

    public HederaTokenStore(
            final ContextOptionValidator validator,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final Store store) {
        this.validator = validator;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        this.store = store;
    }

    private static TokenRelationshipKey asTokenRelationshipKey(AccountID accountID, TokenID tokenID) {
        return new TokenRelationshipKey(asTypedEvmAddress(tokenID), asTypedEvmAddress(accountID));
    }

    private ResponseCodeEnum usabilityOf(final AccountID id) {
        try {
            final var account = store.getAccount(asTypedEvmAddress(id), OnMissing.THROW);
            final var isDeleted = account.isDeleted();
            if (isDeleted) {
                final var isContract = account.isSmartContract();
                return isContract ? CONTRACT_DELETED : ACCOUNT_DELETED;
            }
            return validator.expiryStatusGiven(store, id);
        } catch (final MissingEntityException ignore) {
            return INVALID_ACCOUNT_ID;
        }
    }

    public ResponseCodeEnum autoAssociate(AccountID aId, TokenID tId) {
        return fullySanityChecked(aId, tId, (accountId, tokenId) -> {
            final var tokenRelationship =
                    store.getTokenRelationship(asTokenRelationshipKey(aId, tId), OnMissing.DONT_THROW);

            if (!tokenRelationship.getAccount().getId().equals(Id.DEFAULT)) {
                return TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
            }

            var account = store.getAccount(asTypedEvmAddress(aId), OnMissing.THROW);
            var numAssociations = account.getNumAssociations();

            if (mirrorNodeEvmProperties.isLimitTokenAssociations()
                    && numAssociations == mirrorNodeEvmProperties.getMaxTokensPerAccount()) {
                return TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
            }

            var validity = OK;
            var maxAutomaticAssociations = account.getMaxAutomaticAssociations();
            var alreadyUsedAutomaticAssociations = account.getAlreadyUsedAutomaticAssociations();

            if (alreadyUsedAutomaticAssociations >= maxAutomaticAssociations) {
                validity = NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
            }

            if (validity == OK) {
                final var token = get(tId);

                final var newTokenRelationship = new TokenRelationship(token, account, true)
                        .setFrozen(token.hasFreezeKey() && token.isFrozenByDefault())
                        .setKycGranted(!token.hasKycKey())
                        .setAutomaticAssociation(true);

                numAssociations++;
                final var newAccount = account.setNumAssociations(numAssociations)
                        .setAutoAssociationMetadata(setAlreadyUsedAutomaticAssociationsTo(
                                account.getAutoAssociationMetadata(), alreadyUsedAutomaticAssociations + 1));

                store.updateTokenRelationship(newTokenRelationship);
                store.updateAccount(newAccount);
            }
            return validity;
        });
    }

    public boolean exists(final TokenID id) {
        final var token = store.getToken(asTypedEvmAddress(id), OnMissing.DONT_THROW);
        return !token.getId().equals(Id.DEFAULT);
    }

    public Token get(final TokenID id) {
        final var token = store.getToken(asTypedEvmAddress(id), OnMissing.DONT_THROW);

        if (token.getId().equals(Id.DEFAULT)) {
            throw new IllegalArgumentException(
                    String.format("Argument 'id=%s' does not refer to a known token!", readableId(id)));
        }

        return token;
    }

    public ResponseCodeEnum adjustBalance(final AccountID aId, final TokenID tId, final long adjustment) {
        return sanityCheckedFungibleCommon(aId, tId, token -> tryAdjustment(aId, tId, adjustment));
    }

    public ResponseCodeEnum changeOwner(final NftId nftId, final AccountID from, final AccountID to) {
        final var tId = nftId.tokenId();
        return sanityChecked(false, from, to, tId, token -> {
            final var nft = store.getUniqueToken(nftId, OnMissing.DONT_THROW);
            if (nft.getTokenId().equals(Id.DEFAULT)) {
                return INVALID_NFT_ID;
            }

            final var fromFreezeAndKycValidity = checkRelFrozenAndKycProps(from, tId);
            if (fromFreezeAndKycValidity != OK) {
                return fromFreezeAndKycValidity;
            }
            final var toFreezeAndKycValidity = checkRelFrozenAndKycProps(to, tId);
            if (toFreezeAndKycValidity != OK) {
                return toFreezeAndKycValidity;
            }

            final var tid = nftId.tokenId();
            final var tokenTreasury = store.getToken(asTypedEvmAddress(tid), OnMissing.THROW)
                    .getTreasury()
                    .getId();
            var owner = nft.getOwner();
            if (owner.equals(Id.DEFAULT)) {
                owner = tokenTreasury;
            }
            if (!owner.equals(Id.fromGrpcAccount(from))) {
                return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
            }

            updateStore(nftId, from, to, toGrpcAccountId(tokenTreasury));
            return OK;
        });
    }

    private void updateStore(
            final NftId nftId, final AccountID from, final AccountID to, final AccountID tokenTreasury) {
        final var nftType = nftId.tokenId();
        final var fromRel = asTokenRelationshipKey(from, nftType);
        final var toRel = asTokenRelationshipKey(to, nftType);

        final var fromAccount = store.getAccount(asTypedEvmAddress(from), OnMissing.THROW);
        final var toAccount = store.getAccount(asTypedEvmAddress(to), OnMissing.THROW);
        final var fromRelation = store.getTokenRelationship(fromRel, OnMissing.THROW);
        final var toRelation = store.getTokenRelationship(toRel, OnMissing.THROW);

        final var fromNftsOwned = fromAccount.getOwnedNfts();
        final var fromThisNftsOwned = fromRelation.getBalance();
        final var toNftsOwned = toAccount.getOwnedNfts();
        final var toThisNftsOwned = toRelation.getBalance();
        final var fromNumPositiveBalances = fromAccount.getNumPositiveBalances();
        final var toNumPositiveBalances = toAccount.getNumPositiveBalances();
        final var isTreasuryReturn = tokenTreasury.equals(to);

        final var nft = store.getUniqueToken(nftId, OnMissing.THROW);
        UniqueToken newNft;
        if (isTreasuryReturn) {
            newNft = nft.setOwner(Id.DEFAULT);
        } else {
            newNft = nft.setOwner(Id.fromGrpcAccount(to));
        }
        store.updateUniqueToken(newNft);

        final var updatedFromPositiveBalances =
                fromThisNftsOwned - 1 == 0 ? fromNumPositiveBalances - 1 : fromNumPositiveBalances;
        final var updatedToNumPositiveBalances =
                toThisNftsOwned == 0 ? toNumPositiveBalances + 1 : toNumPositiveBalances;

        var newFromAccount =
                fromAccount.setOwnedNfts(fromNftsOwned - 1).setNumPositiveBalances(updatedFromPositiveBalances);

        var newToAccount = toAccount.setOwnedNfts(toNftsOwned + 1).setNumPositiveBalances(updatedToNumPositiveBalances);

        var newFromRelation = fromRelation.setBalance(fromThisNftsOwned - 1);
        var newToRelation = toRelation.setBalance(toThisNftsOwned + 1);

        // Note correctness here depends on rejecting self-exchanges
        store.updateAccount(newFromAccount);
        store.updateAccount(newToAccount);
        store.updateTokenRelationship(newFromRelation);
        store.updateTokenRelationship(newToRelation);
    }

    public boolean matchesTokenDecimals(final TokenID tId, final int expectedDecimals) {
        return get(tId).getDecimals() == expectedDecimals;
    }

    private ResponseCodeEnum tryAdjustment(final AccountID aId, final TokenID tId, final long adjustment) {
        final var freezeAndKycValidity = checkRelFrozenAndKycProps(aId, tId);
        if (!freezeAndKycValidity.equals(OK)) {
            return freezeAndKycValidity;
        }

        final var relationship = asTokenRelationshipKey(aId, tId);

        final var tokenRelationship = store.getTokenRelationship(relationship, OnMissing.THROW);

        final var balance = tokenRelationship.getBalance();
        final var newBalance = balance + adjustment;
        if (newBalance < 0) {
            return INSUFFICIENT_TOKEN_BALANCE;
        }

        final var newTokenRelationship = tokenRelationship.setBalance(newBalance);
        store.updateTokenRelationship(newTokenRelationship);

        final var account = store.getAccount(asTypedEvmAddress(aId), OnMissing.THROW);
        int numPositiveBalances = account.getNumPositiveBalances();

        // If the original balance is zero, then the receiving account's numPositiveBalances has to
        // be increased
        // and if the newBalance is zero, then the sending account's numPositiveBalances has to be
        // decreased
        if (newBalance == 0 && adjustment < 0) {
            numPositiveBalances--;
        } else if (balance == 0 && adjustment > 0) {
            numPositiveBalances++;
        }

        final var newAccount = account.setNumPositiveBalances(numPositiveBalances);
        store.updateAccount(newAccount);
        return OK;
    }

    private ResponseCodeEnum checkRelFrozenAndKycProps(final AccountID aId, final TokenID tId) {
        final var relationship = asTokenRelationshipKey(aId, tId);
        final var tokenRelationship = store.getTokenRelationship(relationship, OnMissing.THROW);

        if (tokenRelationship.isFrozen()) {
            return ACCOUNT_FROZEN_FOR_TOKEN;
        }
        if (!tokenRelationship.isKycGranted()) {
            return ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
        }
        return OK;
    }

    private ResponseCodeEnum fullySanityChecked(
            final AccountID aId, final TokenID tId, final BiFunction<AccountID, TokenID, ResponseCodeEnum> action) {
        final var validity = usabilityOf(aId);
        if (validity != OK) {
            return validity;
        }
        final var id = resolve(tId);
        if (id == MISSING_TOKEN) {
            return INVALID_TOKEN_ID;
        }
        final var token = get(id);
        if (token.isDeleted()) {
            return TOKEN_WAS_DELETED;
        }
        return action.apply(aId, tId);
    }

    private ResponseCodeEnum sanityCheckedFungibleCommon(
            final AccountID aId, final TokenID tId, final Function<Token, ResponseCodeEnum> action) {
        return sanityChecked(true, aId, null, tId, action);
    }

    @SuppressWarnings("java:S3776")
    private ResponseCodeEnum sanityChecked(
            final boolean onlyFungibleCommon,
            final AccountID aId,
            final AccountID aCounterPartyId,
            final TokenID tId,
            final Function<Token, ResponseCodeEnum> action) {
        var validity = usabilityOf(aId);
        if (validity != OK) {
            return validity;
        }
        if (aCounterPartyId != null) {
            validity = usabilityOf(aCounterPartyId);
            if (validity != OK) {
                return validity;
            }
        }

        validity = checkTokenExistence(tId);
        if (validity != OK) {
            return validity;
        }

        final var token = get(tId);
        if (token.isDeleted()) {
            return TOKEN_WAS_DELETED;
        }
        if (token.isPaused()) {
            return TOKEN_IS_PAUSED;
        }
        if (onlyFungibleCommon && token.getType() == NON_FUNGIBLE_UNIQUE) {
            return ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
        }

        var key = asTokenRelationshipKey(aId, tId);
        var tokenRelationship = store.getTokenRelationship(key, OnMissing.DONT_THROW);

        /*
         * Instead of returning  TOKEN_NOT_ASSOCIATED_TO_ACCOUNT when a token is not associated,
         * we check if the account has any maxAutoAssociations set up, if they do check if we reached the limit and
         * auto associate. If not return EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT
         */
        if (tokenRelationship.getAccount().getId().equals(Id.DEFAULT)) {
            validity = validateAndAutoAssociate(aId, tId);
            if (validity != OK) {
                return validity;
            }
        }
        if (aCounterPartyId != null) {
            key = asTokenRelationshipKey(aCounterPartyId, tId);
            tokenRelationship = store.getTokenRelationship(key, OnMissing.DONT_THROW);
            if (tokenRelationship.getAccount().getId().equals(Id.DEFAULT)) {
                validity = validateAndAutoAssociate(aCounterPartyId, tId);
                if (validity != OK) {
                    return validity;
                }
            }
        }

        return action.apply(token);
    }

    private ResponseCodeEnum validateAndAutoAssociate(AccountID aId, TokenID tId) {
        final var account = store.getAccount(asTypedEvmAddress(aId), OnMissing.THROW);
        if (account.getMaxAutomaticAssociations() > 0) {
            return autoAssociate(aId, tId);
        }
        return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
    }

    private ResponseCodeEnum checkTokenExistence(final TokenID tId) {
        return exists(tId) ? OK : INVALID_TOKEN_ID;
    }

    TokenID resolve(TokenID id) {
        return exists(id) ? id : MISSING_TOKEN;
    }

    public ResponseCodeEnum tryTokenChange(BalanceChange change) {
        var validity = OK;
        var tokenId = resolve(change.tokenId());
        if (tokenId == MISSING_TOKEN) {
            validity = INVALID_TOKEN_ID;
        }
        if (change.hasExpectedDecimals() && !matchesTokenDecimals(change.tokenId(), change.getExpectedDecimals())) {
            validity = UNEXPECTED_TOKEN_DECIMALS;
        }
        if (validity == OK) {
            if (change.isForNft()) {
                validity = changeOwner(change.nftId(), change.accountId(), change.counterPartyAccountId());
            } else {
                validity = adjustBalance(change.accountId(), tokenId, change.getAggregatedUnits());
                if (validity == INSUFFICIENT_TOKEN_BALANCE) {
                    validity = change.codeForInsufficientBalance();
                }
            }
        }
        return validity;
    }
}
