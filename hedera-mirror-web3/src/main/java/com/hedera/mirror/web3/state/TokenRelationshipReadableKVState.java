/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.common.domain.token.TokenTypeEnum;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenBalanceRepository;
import com.hedera.mirror.web3.repository.TokenRepository;
import com.hedera.mirror.web3.utils.Suppliers;
import com.swirlds.state.spi.ReadableKVStateBase;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

@Named
public class TokenRelationshipReadableKVState extends ReadableKVStateBase<EntityIDPair, TokenRelation> {

    private final CommonEntityAccessor commonEntityAccessor;
    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenRepository tokenRepository;

    protected TokenRelationshipReadableKVState(
            final CommonEntityAccessor commonEntityAccessor,
            final NftRepository nftRepository,
            final TokenAccountRepository tokenAccountRepository,
            final TokenBalanceRepository tokenBalanceRepository,
            final TokenRepository tokenRepository) {
        super("TOKEN_RELS");
        this.nftRepository = nftRepository;
        this.commonEntityAccessor = commonEntityAccessor;
        this.tokenAccountRepository = tokenAccountRepository;
        this.tokenBalanceRepository = tokenBalanceRepository;
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected TokenRelation readFromDataSource(@Nonnull EntityIDPair key) {
        final var tokenId = key.tokenId();
        final var accountId = key.accountId();
        if (tokenId == null || accountId == null) {
            return null;
        }
        if (AccountID.DEFAULT.equals(accountId) || TokenID.DEFAULT.equals(tokenId)) return null;

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var account = findAccount(accountId, timestamp);
        if (account.isEmpty()) {
            return null;
        }

        // The accountId will always be in the format "shard.realm.num"
        return findTokenAccount(tokenId, accountId, timestamp)
                .map(ta -> tokenRelationFromEntity(tokenId, accountId, ta, timestamp))
                .orElse(null);
    }

    @Nonnull
    @Override
    protected Iterator<EntityIDPair> iterateFromDataSource() {
        return Collections.emptyIterator();
    }

    @Override
    public long size() {
        return 0;
    }

    private Optional<TokenAccount> findTokenAccount(
            final TokenID tokenID, final AccountID accountID, final Optional<Long> timestamp) {
        AbstractTokenAccount.Id id = new AbstractTokenAccount.Id();
        id.setTokenId(toEntityId(tokenID).getId());
        id.setAccountId(toEntityId(accountID).getId());
        return timestamp
                .map(t -> tokenAccountRepository.findByIdAndTimestamp(id.getAccountId(), id.getTokenId(), t))
                .orElseGet(() -> tokenAccountRepository.findById(id));
    }

    private TokenRelation tokenRelationFromEntity(
            final TokenID tokenID,
            final AccountID accountID,
            final TokenAccount tokenAccount,
            final Optional<Long> timestamp) {
        return TokenRelation.newBuilder()
                .tokenId(tokenID)
                .accountId(accountID)
                .balanceSupplier(getBalance(tokenAccount, timestamp))
                .frozen(tokenAccount.getFreezeStatus() == TokenFreezeStatusEnum.FROZEN)
                .kycGranted(tokenAccount.getKycStatus() != TokenKycStatusEnum.REVOKED)
                .automaticAssociation(tokenAccount.getAutomaticAssociation())
                .build();
    }

    /**
     * For the latest block we have the balance directly as a field in the TokenAccount object.
     * For the historical block we need to execute a query to calculate the historical balance, but
     * we first need to find the token type in order to use the correct repository.
     */
    private Supplier<Long> getBalance(final TokenAccount tokenAccount, final Optional<Long> timestamp) {
        if (timestamp.isEmpty()) {
            return tokenAccount::getBalance;
        }

        final var tokenType = findTokenType(tokenAccount.getTokenId());
        return tokenType
                .map(tokenTypeEnum -> Suppliers.memoize(() -> tokenTypeEnum.equals(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                        ? getNftBalance(tokenAccount, timestamp.get())
                        : getFungibleBalance(tokenAccount, timestamp.get())))
                .orElseGet(() -> () -> 0L);
    }

    private Long getNftBalance(final TokenAccount tokenAccount, final long timestamp) {
        return nftRepository
                .nftBalanceByAccountIdTokenIdAndTimestamp(
                        tokenAccount.getAccountId(), tokenAccount.getTokenId(), timestamp)
                .orElse(0L);
    }

    private Long getFungibleBalance(final TokenAccount tokenAccount, final long timestamp) {
        return tokenBalanceRepository
                .findHistoricalTokenBalanceUpToTimestamp(
                        tokenAccount.getTokenId(), tokenAccount.getAccountId(), timestamp)
                .orElse(0L);
    }

    private Optional<Entity> findAccount(final AccountID accountID, final Optional<Long> timestamp) {
        return commonEntityAccessor.get(accountID, timestamp);
    }

    private Optional<TokenTypeEnum> findTokenType(final long tokenId) {
        return tokenRepository.findTokenTypeById(tokenId);
    }
}
