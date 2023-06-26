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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.web3.evm.store.UpdatableReferenceCache.UpdatableCacheUsageException;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.FcTokenAllowanceId;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;

public class StoreImpl implements Store {

    private final StackedStateFrames<Object> stackedStateFrames;

    public StoreImpl(final List<DatabaseAccessor<Object, ?>> databaseAccessors) {
        this.stackedStateFrames = new StackedStateFrames<>(databaseAccessors);
    }

    @Override
    public Account getAccount(final Address address, final OnMissing throwIfMissing) {
        final var accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        final var account = accountAccessor.get(address);

        if (OnMissing.THROW.equals(throwIfMissing)) {
            return account.orElseThrow(() -> missingEntityException(Account.class, address));
        } else {
            return account.orElse(Account.getEmptyAccount());
        }
    }

    @Override
    public Optional<Entity> getEntity(Address address, OnMissing throwIfMissing) {
        return stackedStateFrames.top().getAccessor(Entity.class).get(address);
    }

    @Override
    public Optional<TokenAccount> getTokenAccount(AbstractTokenAccount.Id id, OnMissing throwIfMissing) {
        return stackedStateFrames.top().getAccessor(TokenAccount.class).get(id);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Optional<List<CustomFee>> getCustomFee(Address address, OnMissing throwIfMissing) {
        final var token = getToken(address, throwIfMissing);
        return Optional.of(token.getCustomFees());
    }

    @Override
    public Long getTokenAllowance(Address address, FcTokenAllowanceId id, OnMissing throwIfMissing) {
        final var account = getAccount(address, throwIfMissing);
        if (account.isEmptyAccount()) return 0L;
        return account.getFungibleTokenAllowances().get(id);
    }

    @Override
    public boolean hasNftAllowance(Address address, FcTokenAllowanceId id, OnMissing throwIfMissing) {
        final var account = getAccount(address, throwIfMissing);
        if (account.isEmptyAccount()) return false;
        return account.getApproveForAllNfts().contains(id);
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
    public TokenRelationship getTokenRelationship(
            final TokenRelationshipKey tokenRelationshipKey, final OnMissing throwIfMissing) {
        final var tokenRelationshipAccessor = stackedStateFrames.top().getAccessor(TokenRelationship.class);
        final var tokenRelationship = tokenRelationshipAccessor.get(tokenRelationshipKey);

        if (OnMissing.THROW.equals(throwIfMissing)) {
            return tokenRelationship.orElseThrow(
                    () -> missingEntityException(TokenRelationship.class, tokenRelationshipKey));
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
            accountAccessor.delete(accountAddress);
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
    public void commit() {
        if (stackedStateFrames.height() > 1) { // commit only to upstream RWCachingStateFrame
            stackedStateFrames.top().commit();
        }
    }

    @Override
    public void wrap() {
        stackedStateFrames.push();
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
