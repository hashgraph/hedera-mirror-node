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

package com.hedera.mirror.web3.evm.store;

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.UpdatableReferenceCache.UpdatableCacheUsageException;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import java.util.HashMap;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public class StoreImpl implements Store {

    private final StackedStateFrames<Object> stackedStateFrames;
    private final MirrorEvmContractAliases mirrorEvmContractAliases;

    public StoreImpl(
            final List<DatabaseAccessor<Object, ?>> databaseAccessors,
            final MirrorEvmContractAliases mirrorEvmContractAliases) {
        this.stackedStateFrames = new StackedStateFrames<>(databaseAccessors);
        this.mirrorEvmContractAliases = mirrorEvmContractAliases;
    }

    @Override
    public Account getAccount(final Address address, final OnMissing throwIfMissing) {
        final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        final var account = accountAccessor.get(mirrorEvmContractAliases.resolveForEvm(address));

        if (OnMissing.THROW.equals(throwIfMissing)) {
            return account.orElseThrow(() -> missingEntityException(Account.class, address));
        } else {
            return account.orElse(Account.getEmptyAccount());
        }
    }

    @Override
    public Token getToken(final Address address, final OnMissing throwIfMissing) {
        final var tokenAccessor = stackedStateFrames.top().getAccessor(Token.class);
        final var token = tokenAccessor.get(address);

        if (OnMissing.THROW.equals(throwIfMissing)) {
            return token.orElseThrow(() -> missingEntityException(Token.class, address));
        } else {
            return token.orElse(Token.getEmptyToken());
        }
    }

    @Override
    public Token loadPossiblyPausedToken(final Address tokenAddress) {
        final var token = getToken(tokenAddress, OnMissing.DONT_THROW);

        validateTrue(!token.isEmptyToken(), INVALID_TOKEN_ID);
        validateFalse(token.isDeleted(), TOKEN_WAS_DELETED);

        return token;
    }

    @Override
    public TokenRelationship getTokenRelationship(
            final TokenRelationshipKey tokenRelationshipKey, final OnMissing throwIfMissing) {
        final var tokenRelationshipAccessor = stackedStateFrames.top().getAccessor(TokenRelationship.class);

        final var resolvedTokenRelationShipKey = new TokenRelationshipKey(
                tokenRelationshipKey.tokenAddress(),
                mirrorEvmContractAliases.resolveForEvm(tokenRelationshipKey.accountAddress()));
        final var tokenRelationship = tokenRelationshipAccessor.get(resolvedTokenRelationShipKey);

        if (OnMissing.THROW.equals(throwIfMissing)) {
            return tokenRelationship.orElseThrow(
                    () -> missingEntityException(TokenRelationship.class, resolvedTokenRelationShipKey));
        } else {
            return tokenRelationship.orElse(TokenRelationship.getEmptyTokenRelationship());
        }
    }

    @Override
    public UniqueToken getUniqueToken(final NftId nftId, final OnMissing throwIfMissing) {
        final var uniqueTokenAccessor = stackedStateFrames.top().getAccessor(UniqueToken.class);
        final var uniqueToken = uniqueTokenAccessor.get(nftId);

        if (OnMissing.THROW.equals(throwIfMissing)) {
            return uniqueToken.orElseThrow(() -> missingEntityException(UniqueToken.class, nftId));
        } else {
            return uniqueToken.orElse(UniqueToken.getEmptyUniqueToken());
        }
    }

    @Override
    public void updateAccount(final Account updatedAccount) {
        final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        accountAccessor.set(updatedAccount.getAccountAddress(), updatedAccount);
    }

    @Override
    public void deleteAccount(final Address accountAddress) {
        final var topFrame = stackedStateFrames.top();
        final var accountAccessor = topFrame.getAccessor(com.hedera.services.store.models.Account.class);
        try {
            accountAccessor.delete(mirrorEvmContractAliases.resolveForEvm(accountAddress));
        } catch (UpdatableCacheUsageException ex) {
            // ignore, value has been deleted
        }
    }

    @Override
    public void updateTokenRelationship(final TokenRelationship updatedTokenRelationship) {
        final var tokenRelationshipAccessor = stackedStateFrames.top().getAccessor(TokenRelationship.class);
        tokenRelationshipAccessor.set(keyFromRelationship(updatedTokenRelationship), updatedTokenRelationship);
    }

    @Override
    public void updateToken(final Token fungibleToken) {
        final var tokenAccessor = stackedStateFrames.top().getAccessor(Token.class);
        tokenAccessor.set(fungibleToken.getId().asEvmAddress(), fungibleToken);
    }

    @Override
    public void updateUniqueToken(final UniqueToken updatedUniqueToken) {
        final var uniqueTokenAccessor = stackedStateFrames.top().getAccessor(UniqueToken.class);
        uniqueTokenAccessor.set(updatedUniqueToken.getNftId(), updatedUniqueToken);
    }

    @Override
    public boolean hasAssociation(TokenRelationshipKey tokenRelationshipKey) {
        return getTokenRelationship(tokenRelationshipKey, OnMissing.DONT_THROW).hasAssociation();
    }

    @Override
    public void commit() {
        if (stackedStateFrames.height() > 1) { // commit only to upstream RWCachingStateFrame
            stackedStateFrames.top().commit();
        }
    }

    @Override
    public void wrap() {
        stackedStateFrames.push();
    }

    /**
     * Returns a {@link Token} model with loaded unique tokens
     *
     * @param token         the token model, on which to load the unique tokens
     * @param serialNumbers the serial numbers to load
     * @throws com.hedera.node.app.service.evm.exceptions.InvalidTransactionException if the requested token class is missing, deleted, or
     *                                                                                expired and pending removal
     */
    public Token loadUniqueTokens(final Token token, final List<Long> serialNumbers) {
        final var loadedUniqueTokens = new HashMap<Long, UniqueToken>();
        for (final long serialNumber : serialNumbers) {
            final var uniqueToken = loadUniqueToken(token.getId(), serialNumber);
            loadedUniqueTokens.put(serialNumber, uniqueToken);
        }

        return token.setLoadedUniqueTokens(loadedUniqueTokens);
    }

    /**
     * Returns a {@link UniqueToken} model of the requested unique token, with operations that can
     * be used to implement business logic in a transaction.
     *
     * @param tokenId   TokenId of the NFT
     * @param serialNum Serial number of the NFT
     * @return The {@link UniqueToken} model of the requested unique token
     */
    private UniqueToken loadUniqueToken(final Id tokenId, final Long serialNum) {
        final var nftId = new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), serialNum);
        return getUniqueToken(nftId, OnMissing.THROW);
    }

    private TokenRelationshipKey keyFromRelationship(TokenRelationship tokenRelationship) {
        final var tokenAddress = tokenRelationship.getToken().getId().asEvmAddress();
        final var accountAddress = tokenRelationship.getAccount().getAccountAddress();
        return new TokenRelationshipKey(tokenAddress, accountAddress);
    }

    private InvalidTransactionException missingEntityException(final Class<?> type, Object id) {
        return new InvalidTransactionException(
                FAIL_INVALID, String.format("Entity of type %s with id %s is missing", type.getName(), id), "");
    }
}
