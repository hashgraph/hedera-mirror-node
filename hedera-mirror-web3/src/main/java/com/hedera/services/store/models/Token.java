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
import static com.hedera.services.utils.BitPackUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Encapsulates the state and operations of a Hedera token.
 *
 * <p>Operations are validated, and throw a {@link InvalidTransactionException} with response code
 * capturing the failure when one occurs.
 *
 * <p><b>NOTE:</b> Some operations only apply to specific token types. For example, a {@link
 * Token#mint(TokenRelationship, long, boolean)} call only makes sense for a token of type {@code FUNGIBLE_COMMON}; the
 * signature for a {@code NON_FUNGIBLE_UNIQUE} is
 * {@link Token#mint(OwnershipTracker, TokenRelationship, List, RichInstant)}.
 * <p>
 * This model is used as a value in a special state (CachingStateFrame), used for speculative write operations. Object
 * immutability is required for this model in order to be used seamlessly in the state.
 */
public class Token {
    private final Id id;
    private final List<UniqueToken> mintedUniqueTokens;
    private final List<UniqueToken> removedUniqueTokens;
    private final Map<Long, UniqueToken> loadedUniqueTokens;
    private final boolean supplyHasChanged;
    private final TokenType type;
    private final TokenSupplyType supplyType;
    private final long totalSupply;
    private final long maxSupply;
    private final JKey kycKey;
    private final JKey freezeKey;
    private final JKey supplyKey;
    private final JKey wipeKey;
    private final JKey adminKey;
    private final JKey feeScheduleKey;
    private final JKey pauseKey;
    private final boolean frozenByDefault;
    private final Account treasury;
    private final Account autoRenewAccount;
    private final boolean deleted;
    private final boolean paused;
    private final boolean autoRemoved;
    private final long expiry;
    private final boolean isNew;
    private final String memo;
    private final String name;
    private final String symbol;
    private final int decimals;
    private final long autoRenewPeriod;
    private final long lastUsedSerialNumber;
    private final List<CustomFee> customFees;

    @SuppressWarnings("java:S107")
    public Token(
            Id id,
            List<UniqueToken> mintedUniqueTokens,
            List<UniqueToken> removedUniqueTokens,
            Map<Long, UniqueToken> loadedUniqueTokens,
            boolean supplyHasChanged,
            TokenType type,
            TokenSupplyType supplyType,
            long totalSupply,
            long maxSupply,
            JKey kycKey,
            JKey freezeKey,
            JKey supplyKey,
            JKey wipeKey,
            JKey adminKey,
            JKey feeScheduleKey,
            JKey pauseKey,
            boolean frozenByDefault,
            Account treasury,
            Account autoRenewAccount,
            boolean deleted,
            boolean paused,
            boolean autoRemoved,
            long expiry,
            boolean isNew,
            String memo,
            String name,
            String symbol,
            int decimals,
            long autoRenewPeriod,
            long lastUsedSerialNumber,
            List<CustomFee> customFees) {
        this.id = id;
        this.mintedUniqueTokens = mintedUniqueTokens;
        this.removedUniqueTokens = removedUniqueTokens;
        this.loadedUniqueTokens = loadedUniqueTokens;
        this.supplyHasChanged = supplyHasChanged;
        this.type = type;
        this.supplyType = supplyType;
        this.totalSupply = totalSupply;
        this.maxSupply = maxSupply;
        this.kycKey = kycKey;
        this.freezeKey = freezeKey;
        this.supplyKey = supplyKey;
        this.wipeKey = wipeKey;
        this.adminKey = adminKey;
        this.feeScheduleKey = feeScheduleKey;
        this.pauseKey = pauseKey;
        this.frozenByDefault = frozenByDefault;
        this.treasury = treasury;
        this.autoRenewAccount = autoRenewAccount;
        this.deleted = deleted;
        this.paused = paused;
        this.autoRemoved = autoRemoved;
        this.expiry = expiry;
        this.isNew = isNew;
        this.memo = memo;
        this.name = name;
        this.symbol = symbol;
        this.decimals = decimals;
        this.autoRenewPeriod = autoRenewPeriod;
        this.lastUsedSerialNumber = lastUsedSerialNumber;
        this.customFees = customFees;
    }

    /**
     * Creates new instance of {@link Token} with updated totalSupply in order to keep the object's immutability and
     * avoid entry points for changing the state.
     *
     * @param oldToken
     * @param totalSupply
     * @return the new instance of {@link Token} with updated {@link #totalSupply} property
     */
    private Token createNewTokenWithNewTotalSupply(Token oldToken, long totalSupply) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                true,
                oldToken.type,
                oldToken.supplyType,
                totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated treasury in order to keep the object's
     * immutability and avoid entry points for changing the state.
     *
     * @param oldToken
     * @param treasury
     * @return new instance of {@link Token} with updated {@link #treasury} property
     */
    private Token createNewTokenWithNewTreasury(Token oldToken, Account treasury) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated lastUsedSerialNumber in order to keep the object's
     * immutability and avoid entry points for changing the state.
     *
     * @param oldToken
     * @param lastUsedSerialNumber
     * @return new instance of {@link Token} with updated {@link #lastUsedSerialNumber} property
     */
    private Token createNewTokenWithNewLastUsedSerialNumber(Token oldToken, long lastUsedSerialNumber) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with deleted property in order to keep the object's
     * immutability and avoid entry points for changing the state.
     *
     * @param oldToken
     * @param isDeleted
     * @return new instance of {@link Token} with {@link #deleted} property
     */
    private Token createNewTokenWithDeletedFlag(Token oldToken, boolean isDeleted) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                isDeleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated type in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param tokenType
     * @return new instance of {@link Token} with updated {@link #type} property
     */
    private Token createNewTokenWithTokenType(Token oldToken, TokenType tokenType) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                tokenType,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                oldToken.kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated kycKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param kycKey
     * @return new instance of {@link Token} with updated {@link #kycKey} property
     */
    private Token createNewTokenWithKycKey(Token oldToken, JKey kycKey) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated freezeKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param freezeKey
     * @return new instance of {@link Token} with updated {@link #freezeKey} property
     */
    private Token createNewTokenWithFreezeKey(Token oldToken, JKey freezeKey) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                kycKey,
                freezeKey,
                oldToken.supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated supplyKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param supplyKey
     * @return new instance of {@link Token} with updated {@link #supplyKey} property
     */
    private Token createNewTokenWithSupplyKey(Token oldToken, JKey supplyKey) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                kycKey,
                oldToken.freezeKey,
                supplyKey,
                oldToken.wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates new instance of {@link Token} with updated wipeKey in order to keep the object's immutability and avoid
     * entry points for changing the state.
     *
     * @param oldToken
     * @param wipeKey
     * @return new instance of {@link Token} with updated {@link #wipeKey} property
     */
    private Token createNewTokenWithWipeKey(Token oldToken, JKey wipeKey) {
        return new Token(
                oldToken.id,
                oldToken.mintedUniqueTokens,
                oldToken.removedUniqueTokens,
                oldToken.loadedUniqueTokens,
                oldToken.supplyHasChanged,
                oldToken.type,
                oldToken.supplyType,
                oldToken.totalSupply,
                oldToken.maxSupply,
                kycKey,
                oldToken.freezeKey,
                oldToken.supplyKey,
                wipeKey,
                oldToken.adminKey,
                oldToken.feeScheduleKey,
                oldToken.pauseKey,
                oldToken.frozenByDefault,
                oldToken.treasury,
                oldToken.autoRenewAccount,
                oldToken.deleted,
                oldToken.paused,
                oldToken.autoRemoved,
                oldToken.expiry,
                oldToken.isNew,
                oldToken.memo,
                oldToken.name,
                oldToken.symbol,
                oldToken.decimals,
                oldToken.autoRenewPeriod,
                oldToken.lastUsedSerialNumber,
                oldToken.customFees);
    }

    /**
     * Creates a new instance of the model token, which is later persisted in state.
     *
     * @param tokenId            the new token id
     * @param op                 the transaction body containing the necessary data for token creation
     * @param treasury           treasury of the token
     * @param autoRenewAccount   optional(nullable) account used for auto-renewal
     * @param consensusTimestamp the consensus time of the token create transaction
     * @return a new instance of the {@link Token} class
     */
    public static Token fromGrpcOpAndMeta(
            final Id tokenId,
            final TokenCreateTransactionBody op,
            final Account treasury,
            @Nullable final Account autoRenewAccount,
            final long consensusTimestamp) {
        final var tokenExpiry = op.hasAutoRenewAccount()
                ? consensusTimestamp + op.getAutoRenewPeriod().getSeconds()
                : op.getExpiry().getSeconds();

        final var freezeKey = asUsableFcKey(op.getFreezeKey());
        final var adminKey = asUsableFcKey(op.getAdminKey());
        final var kycKey = asUsableFcKey(op.getKycKey());
        final var wipeKey = asUsableFcKey(op.getWipeKey());
        final var supplyKey = asUsableFcKey(op.getSupplyKey());
        final var feeScheduleKey = asUsableFcKey(op.getFeeScheduleKey());
        final var pauseKey = asUsableFcKey(op.getPauseKey());
        return new Token(
                tokenId,
                new ArrayList<>(),
                new ArrayList<>(),
                new HashMap<>(),
                false,
                mapToDomain(op.getTokenType()),
                op.getSupplyType(),
                0,
                op.getMaxSupply(),
                kycKey.orElse(null),
                freezeKey.orElse(null),
                supplyKey.orElse(null),
                wipeKey.orElse(null),
                adminKey.orElse(null),
                feeScheduleKey.orElse(null),
                pauseKey.orElse(null),
                op.getFreezeDefault(),
                treasury,
                autoRenewAccount,
                false,
                false,
                false,
                tokenExpiry,
                true,
                op.getMemo(),
                op.getName(),
                op.getSymbol(),
                op.getDecimals(),
                op.getAutoRenewPeriod().getSeconds(),
                0,
                Collections.emptyList());
    }

    // copied from TokenTypesManager in services
    private static TokenType mapToDomain(final com.hederahashgraph.api.proto.java.TokenType grpcType) {
        if (grpcType == com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE) {
            return TokenType.NON_FUNGIBLE_UNIQUE;
        }
        return TokenType.FUNGIBLE_COMMON;
    }

    /**
     * Minting fungible tokens increases the supply and sets new balance to the treasuryRel
     *
     * @param treasuryRel
     * @param amount
     * @param ignoreSupplyKey
     * @return new instance of {@link Token} with updated fields to keep the object's immutability
     */
    public TokenModificationResult mint(
            final TokenRelationship treasuryRel, final long amount, final boolean ignoreSupplyKey) {
        validateTrue(amount >= 0, INVALID_TOKEN_MINT_AMOUNT, errorMessage("mint", amount, treasuryRel));
        validateTrue(
                type == TokenType.FUNGIBLE_COMMON,
                FAIL_INVALID,
                "Fungible mint can be invoked only on fungible token type");

        return changeSupply(treasuryRel, amount, INVALID_TOKEN_MINT_AMOUNT, ignoreSupplyKey);
    }

    /**
     * Minting unique tokens creates new instances of the given base unique token. Increments the serial number of the
     * given base unique token, and assigns each of the numbers to each new unique token instance.
     *
     * @param ownershipTracker - a tracker of changes made to the ownership of the tokens
     * @param treasuryRel      - the relationship between the treasury account and the token
     * @param metadata         - a list of user-defined metadata, related to the nft instances.
     * @param creationTime     - the consensus time of the token mint transaction
     * @return new instance of {@link Token} with updated fields to keep the object's immutability
     */
    public TokenModificationResult mint(
            final OwnershipTracker ownershipTracker,
            final TokenRelationship treasuryRel,
            final List<ByteString> metadata,
            final RichInstant creationTime) {
        final var metadataCount = metadata.size();
        validateFalse(metadata.isEmpty(), INVALID_TOKEN_MINT_METADATA, "Cannot mint zero unique tokens");
        validateTrue(
                type == TokenType.NON_FUNGIBLE_UNIQUE,
                FAIL_INVALID,
                "Non-fungible mint can be invoked only on non-fungible token type");
        validateTrue((lastUsedSerialNumber + metadataCount) <= MAX_NUM_ALLOWED, SERIAL_NUMBER_LIMIT_REACHED);
        var tokenMod = changeSupply(treasuryRel, metadataCount, FAIL_INVALID, false);
        long newLastUsedSerialNumber = this.lastUsedSerialNumber;

        for (final ByteString m : metadata) {
            newLastUsedSerialNumber++;
            // The default sentinel account is used (0.0.0) to represent unique tokens owned by the
            // Treasury
            final var uniqueToken =
                    new UniqueToken(id, newLastUsedSerialNumber, creationTime, Id.DEFAULT, Id.DEFAULT, m.toByteArray());
            mintedUniqueTokens.add(uniqueToken);
            ownershipTracker.add(id, OwnershipTracker.forMinting(treasury.getId(), newLastUsedSerialNumber));
        }
        var newTreasury = treasury.setOwnedNfts(treasury.getOwnedNfts() + metadataCount);
        var newToken = createNewTokenWithNewTreasury(tokenMod.token(), newTreasury);
        return new TokenModificationResult(
                createNewTokenWithNewLastUsedSerialNumber(newToken, newLastUsedSerialNumber),
                tokenMod.tokenRelationship());
    }

    /**
     * Burning fungible tokens reduces the supply and sets new balance to the treasuryRel
     *
     * @param treasuryRel- the relationship between the treasury account and the token
     * @param amount
     * @return new instance of {@link Token} with updated fields to keep the object's immutability
     */
    public TokenModificationResult burn(final TokenRelationship treasuryRel, final long amount) {
        validateTrue(amount >= 0, INVALID_TOKEN_BURN_AMOUNT, errorMessage("burn", amount, treasuryRel));
        return changeSupply(treasuryRel, -amount, INVALID_TOKEN_BURN_AMOUNT, false);
    }

    /**
     * Burning unique tokens effectively destroys them, as well as reduces the total supply of the token.
     *
     * @param ownershipTracker - a tracker of changes made to the nft ownership
     * @param treasuryRel-     the relationship between the treasury account and the token
     * @param serialNumbers    - the serial numbers, representing the unique tokens which will be
     *                         destroyed.
     */
    public TokenModificationResult burn(
            final OwnershipTracker ownershipTracker,
            final TokenRelationship treasuryRel,
            final List<Long> serialNumbers) {
        validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID);
        validateFalse(serialNumbers.isEmpty(), INVALID_TOKEN_BURN_METADATA);
        final var treasuryId = treasury.getId();
        for (final long serialNum : serialNumbers) {
            final var uniqueToken = loadedUniqueTokens.get(serialNum);
            validateTrue(uniqueToken != null, FAIL_INVALID);

            final var treasuryIsOwner = uniqueToken.getOwner().equals(Id.DEFAULT);
            validateTrue(treasuryIsOwner, TREASURY_MUST_OWN_BURNED_NFT);
            ownershipTracker.add(id, OwnershipTracker.forRemoving(treasuryId, serialNum));
            removedUniqueTokens.add(
                    new UniqueToken(id, serialNum, RichInstant.MISSING_INSTANT, treasuryId, Id.DEFAULT, new byte[] {}));
        }
        final var numBurned = serialNumbers.size();
        var newTreasury = treasury.setOwnedNfts(treasury.getOwnedNfts() - numBurned);
        var tokenMod = changeSupply(treasuryRel, -numBurned, FAIL_INVALID, false);
        var newToken = createNewTokenWithNewTreasury(tokenMod.token(), newTreasury);
        return new TokenModificationResult(newToken, tokenMod.tokenRelationship());
    }

    /**
     * Wiping fungible tokens removes the balance of the given account, as well as reduces the total supply.
     *
     * @param accountRel - the relationship between the account which owns the tokens and the token
     * @param amount     - amount to be wiped
     * @return new instance of {@link Token} with updated fields to keep the object's immutability
     */
    public TokenModificationResult wipe(final TokenRelationship accountRel, final long amount) {
        validateTrue(
                type == TokenType.FUNGIBLE_COMMON,
                FAIL_INVALID,
                "Fungible wipe can be invoked only on Fungible token type.");
        baseWipeValidations(accountRel);
        amountWipeValidations(accountRel, amount);

        final var newTotalSupply = totalSupply - amount;
        final var newAccBalance = accountRel.getBalance() - amount;

        var newAccountRel = accountRel;
        if (newAccBalance == 0) {
            final var currentNumPositiveBalances = accountRel.getAccount().getNumPositiveBalances();
            var newAccount = accountRel.getAccount().setNumPositiveBalances(currentNumPositiveBalances - 1);
            newAccountRel = accountRel.setAccount(newAccount);
        }
        return new TokenModificationResult(
                createNewTokenWithNewTotalSupply(this, newTotalSupply), newAccountRel.setBalance(newAccBalance));
    }

    /**
     * Wiping unique tokens removes the unique token instances, associated to the given account, as
     * well as reduces the total supply.
     *
     * @param ownershipTracker - a tracker of changes made to the ownership of the tokens
     * @param accountRel       - the relationship between the account, which owns the tokens, and the
     *                         token
     * @param serialNumbers    - a list of serial numbers, representing the tokens to be wiped
     */
    public TokenModificationResult wipe(
            final OwnershipTracker ownershipTracker,
            final TokenRelationship accountRel,
            final List<Long> serialNumbers) {
        validateTrue(type == TokenType.NON_FUNGIBLE_UNIQUE, FAIL_INVALID);
        validateFalse(serialNumbers.isEmpty(), INVALID_WIPING_AMOUNT);

        baseWipeValidations(accountRel);
        for (final var serialNum : serialNumbers) {
            final var uniqueToken = loadedUniqueTokens.get(serialNum);
            validateTrue(uniqueToken != null, FAIL_INVALID);
            final var wipeAccountIsOwner =
                    uniqueToken.getOwner().equals(accountRel.getAccount().getId());
            validateTrue(wipeAccountIsOwner, ACCOUNT_DOES_NOT_OWN_WIPED_NFT);
        }

        final var newTotalSupply = totalSupply - serialNumbers.size();
        final var newAccountBalance = accountRel.getBalance() - serialNumbers.size();
        var account = accountRel.getAccount();
        for (final long serialNum : serialNumbers) {
            ownershipTracker.add(id, OwnershipTracker.forRemoving(account.getId(), serialNum));
            removedUniqueTokens.add(new UniqueToken(
                    id, serialNum, RichInstant.MISSING_INSTANT, account.getId(), Id.DEFAULT, new byte[] {}));
        }

        if (newAccountBalance == 0) {
            final var currentNumPositiveBalances = account.getNumPositiveBalances();
            account = account.setNumPositiveBalances(currentNumPositiveBalances - 1);
        }

        var newAccountRel = accountRel.setAccount(account.setOwnedNfts(account.getOwnedNfts() - serialNumbers.size()));
        newAccountRel = newAccountRel.setBalance(newAccountBalance);
        return new TokenModificationResult(createNewTokenWithNewTotalSupply(this, newTotalSupply), newAccountRel);
    }

    public TokenRelationship newRelationshipWith(final Account account, final boolean automaticAssociation) {
        var newRel =
                new TokenRelationship(this, account, 0, false, !hasKycKey(), false, false, automaticAssociation, 0);
        if (hasFreezeKey() && frozenByDefault) {
            newRel = newRel.setFrozen(true);
        }
        return newRel;
    }

    /**
     * Creates new {@link TokenRelationship} for the specified {@link Account} IMPORTANT: The provided account is set to
     * KYC granted and unfrozen by default
     *
     * @param account the Account for which to create the relationship
     * @return newly created {@link TokenRelationship}
     */
    public TokenRelationship newEnabledRelationship(final Account account) {
        return new TokenRelationship(this, account, 0, false, true, false, false, false, 0);
    }

    private TokenModificationResult changeSupply(
            final TokenRelationship treasuryRel,
            final long amount,
            final ResponseCodeEnum negSupplyCode,
            final boolean ignoreSupplyKey) {
        validateTrue(treasuryRel != null, FAIL_INVALID, "Cannot mint with a null treasuryRel");
        validateTrue(
                treasuryRel.hasInvolvedIds(id, treasury.getId()),
                FAIL_INVALID,
                "Cannot change " + this + " supply (" + amount + ") with non-treasury rel " + treasuryRel);
        if (!ignoreSupplyKey) {
            validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);
        }
        final long newTotalSupply = totalSupply + amount;
        validateTrue(newTotalSupply >= 0, negSupplyCode);
        if (supplyType == TokenSupplyType.FINITE) {
            validateTrue(
                    maxSupply >= newTotalSupply,
                    TOKEN_MAX_SUPPLY_REACHED,
                    "Cannot mint new supply (" + amount + "). Max supply (" + maxSupply + ") reached");
        }
        var treasuryAccount = treasuryRel.getAccount();
        final long newTreasuryBalance = treasuryRel.getBalance() + amount;
        validateTrue(newTreasuryBalance >= 0, INSUFFICIENT_TOKEN_BALANCE);
        if (treasuryRel.getBalance() == 0 && amount > 0) {
            // for mint op
            treasuryAccount = treasuryAccount.setNumPositiveBalances(treasuryAccount.getNumPositiveBalances() + 1);
        } else if (newTreasuryBalance == 0 && amount < 0) {
            // for burn op
            treasuryAccount = treasuryAccount.setNumPositiveBalances(treasuryAccount.getNumPositiveBalances() - 1);
        }
        var newTreasuryRel = treasuryRel.setAccount(treasuryAccount);
        newTreasuryRel = newTreasuryRel.setBalance(newTreasuryBalance);
        return new TokenModificationResult(createNewTokenWithNewTotalSupply(this, newTotalSupply), newTreasuryRel);
    }

    private void baseWipeValidations(final TokenRelationship accountRel) {
        validateTrue(hasWipeKey(), TOKEN_HAS_NO_WIPE_KEY, "Cannot wipe Tokens without wipe key.");

        validateFalse(
                treasury.getId().equals(accountRel.getAccount().getId()),
                CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT,
                "Cannot wipe treasury account of token.");
    }

    private void amountWipeValidations(final TokenRelationship accountRel, final long amount) {
        validateTrue(amount >= 0, INVALID_WIPING_AMOUNT, errorMessage("wipe", amount, accountRel));

        final var newTotalSupply = totalSupply - amount;
        validateTrue(
                newTotalSupply >= 0, INVALID_WIPING_AMOUNT, "Wiping would negate the total supply of the given token.");

        final var newAccountBalance = accountRel.getBalance() - amount;
        validateTrue(newAccountBalance >= 0, INVALID_WIPING_AMOUNT, "Wiping would negate account balance");
    }

    private String errorMessage(final String op, final long amount, final TokenRelationship rel) {
        return "Cannot " + op + " " + amount + " units of " + this + " from " + rel;
    }

    public Token delete() {
        validateTrue(hasAdminKey(), TOKEN_IS_IMMUTABLE);
        return createNewTokenWithDeletedFlag(this, true);
    }

    public Token setIsDeleted(boolean isDeleted) {
        return createNewTokenWithDeletedFlag(this, isDeleted);
    }

    public Token setLastUsedSerialNumber(final long lastUsedSerialNumber) {
        return createNewTokenWithNewLastUsedSerialNumber(this, lastUsedSerialNumber);
    }

    public Token setType(TokenType tokenType) {
        return createNewTokenWithTokenType(this, tokenType);
    }

    public Token setKycKey(JKey kycKey) {
        return createNewTokenWithKycKey(this, kycKey);
    }

    public Token setFreezeKey(JKey freezeKey) {
        return createNewTokenWithFreezeKey(this, freezeKey);
    }

    public Token setWipeKey(JKey wipeKey) {
        return createNewTokenWithWipeKey(this, wipeKey);
    }

    public Token setSupplyKey(JKey supplyKey) {
        return createNewTokenWithSupplyKey(this, supplyKey);
    }

    public boolean hasAdminKey() {
        return adminKey != null;
    }

    public Account getTreasury() {
        return treasury;
    }

    public Account getAutoRenewAccount() {
        return autoRenewAccount;
    }

    public long getTotalSupply() {
        return totalSupply;
    }

    public long getMaxSupply() {
        return maxSupply;
    }

    public JKey getSupplyKey() {
        return supplyKey;
    }

    public boolean hasFreezeKey() {
        return freezeKey != null;
    }

    public boolean hasKycKey() {
        return kycKey != null;
    }

    private boolean hasWipeKey() {
        return wipeKey != null;
    }

    public JKey getWipeKey() {
        return wipeKey;
    }

    public JKey getKycKey() {
        return kycKey;
    }

    public JKey getFreezeKey() {
        return freezeKey;
    }

    public JKey getPauseKey() {
        return pauseKey;
    }

    public boolean hasPauseKey() {
        return pauseKey != null;
    }

    /* supply is changed only after the token is created */
    public boolean hasChangedSupply() {
        return supplyHasChanged && !isNew;
    }

    public boolean isFrozenByDefault() {
        return frozenByDefault;
    }

    public boolean isPaused() {
        return paused;
    }

    public Id getId() {
        return id;
    }

    public TokenType getType() {
        return type;
    }

    public boolean isFungibleCommon() {
        return type == TokenType.FUNGIBLE_COMMON;
    }

    public boolean isNonFungibleUnique() {
        return type == TokenType.NON_FUNGIBLE_UNIQUE;
    }

    public long getLastUsedSerialNumber() {
        return lastUsedSerialNumber;
    }

    public List<CustomFee> getCustomFees() {
        return customFees;
    }

    public boolean hasMintedUniqueTokens() {
        return !mintedUniqueTokens.isEmpty();
    }

    public List<UniqueToken> mintedUniqueTokens() {
        return mintedUniqueTokens;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public long getExpiry() {
        return expiry;
    }

    public boolean hasRemovedUniqueTokens() {
        return !removedUniqueTokens.isEmpty();
    }

    public List<UniqueToken> removedUniqueTokens() {
        return removedUniqueTokens;
    }

    public Map<Long, UniqueToken> getLoadedUniqueTokens() {
        return loadedUniqueTokens;
    }

    public boolean isBelievedToHaveBeenAutoRemoved() {
        return autoRemoved;
    }

    public JKey getAdminKey() {
        return adminKey;
    }

    public String getMemo() {
        return memo;
    }

    public boolean isNew() {
        return isNew;
    }

    public TokenSupplyType getSupplyType() {
        return supplyType;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getDecimals() {
        return decimals;
    }

    public long getAutoRenewPeriod() {
        return autoRenewPeriod;
    }

    /* NOTE: The object methods below are only overridden to improve
    readability of unit tests; this model object is not used in hash-based
    collections, so the performance of these methods doesn't matter. */
    @Override
    public boolean equals(final Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(Token.class)
                .add("id", id)
                .add("type", type)
                .add("deleted", deleted)
                .add("autoRemoved", autoRemoved)
                .add("treasury", treasury)
                .add("autoRenewAccount", autoRenewAccount)
                .add("kycKey", kycKey)
                .add("freezeKey", freezeKey)
                .add("frozenByDefault", frozenByDefault)
                .add("supplyKey", supplyKey)
                .add("currentSerialNumber", lastUsedSerialNumber)
                .add("pauseKey", pauseKey)
                .add("paused", paused)
                .toString();
    }

    public boolean hasFeeScheduleKey() {
        return feeScheduleKey != null;
    }

    public JKey getFeeScheduleKey() {
        return feeScheduleKey;
    }
}
