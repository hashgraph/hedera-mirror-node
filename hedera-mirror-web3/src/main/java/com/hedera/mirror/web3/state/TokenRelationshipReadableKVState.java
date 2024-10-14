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

import static com.hedera.mirror.web3.state.Utils.ZERO_BALANCE_OPTIONAL;
import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
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

    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenRepository tokenRepository;
    private final NftRepository nftRepository;
    private final CommonEntityAccessor commonEntityAccessor;

    protected TokenRelationshipReadableKVState(
            final TokenAccountRepository tokenAccountRepository,
            final TokenBalanceRepository tokenBalanceRepository,
            final TokenRepository tokenRepository,
            final NftRepository nftRepository,
            final CommonEntityAccessor commonEntityAccessor) {
        super("TOKEN_RELS");
        this.tokenAccountRepository = tokenAccountRepository;
        this.tokenBalanceRepository = tokenBalanceRepository;
        this.tokenRepository = tokenRepository;
        this.nftRepository = nftRepository;
        this.commonEntityAccessor = commonEntityAccessor;
    }

    @Override
    protected TokenRelation readFromDataSource(@Nonnull EntityIDPair key) {
        final var tokenId = key.tokenId();
        final var accountId = key.accountId();
        if (tokenId == null || accountId == null) {
            return null;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        final var account = findAccount(accountId, timestamp);
        if (account.isEmpty()) {
            return null;
        }

        final var tokenType = findTokenType(tokenId);
        if (tokenType.isEmpty()) {
            return null;
        }

        final var tokenAccount = findTokenAccount(tokenId, accountId, timestamp);
        if (tokenAccount.isEmpty()) {
            return null;
        }

        return tokenAccount
                .map(ta -> tokenRelationFromEntity(tokenType.get(), tokenId, account.get(), ta, timestamp))
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

    private EntityId mapAccountAliasToEntityId(final AccountID accountID, final Optional<Long> timestamp) {
        if (accountID.hasAccountNum()) {
            return toEntityId(accountID);
        }
        return commonEntityAccessor
                .get(accountID, timestamp)
                .map(AbstractEntity::toEntityId)
                .orElse(EntityId.EMPTY);
    }

    private Optional<TokenAccount> findTokenAccount(
            final TokenID tokenID, final AccountID accountID, final Optional<Long> timestamp) {
        AbstractTokenAccount.Id id = new AbstractTokenAccount.Id();
        id.setTokenId(toEntityId(tokenID).getId());
        id.setAccountId(mapAccountAliasToEntityId(accountID, timestamp).getId());
        return timestamp
                .map(t -> tokenAccountRepository.findByIdAndTimestamp(id.getAccountId(), id.getTokenId(), t))
                .orElseGet(() -> tokenAccountRepository.findById(id));
    }

    private TokenRelation tokenRelationFromEntity(
            final TokenTypeEnum tokenType,
            final TokenID tokenID,
            final Entity account,
            final TokenAccount tokenAccount,
            final Optional<Long> timestamp) {
        return TokenRelation.newBuilder()
                .tokenId(tokenID)
                .accountId(toAccountId(EntityId.of(account.getId())))
                .balanceSupplier(getBalance(account, tokenType, tokenAccount, timestamp))
                .frozen(tokenAccount.getFreezeStatus() == TokenFreezeStatusEnum.FROZEN)
                .kycGranted(tokenAccount.getKycStatus() != TokenKycStatusEnum.REVOKED)
                .automaticAssociation(tokenAccount.getAutomaticAssociation())
                .build();
    }

    /**
     * Determines fungible or NFT balanceSupplier based on block context.
     */
    private Supplier<Long> getBalance(
            final Entity account,
            final TokenTypeEnum tokenType,
            final TokenAccount tokenAccount,
            final Optional<Long> timestamp) {
        if (tokenType.equals(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)) {
            return Suppliers.memoize(() -> getNftBalance(tokenAccount, timestamp, account.getCreatedTimestamp()));
        }
        return Suppliers.memoize(() -> getFungibleBalance(tokenAccount, timestamp, account.getCreatedTimestamp()));
    }

    /**
     * NFT Balance Explanation:
     * Non-historical Call:
     * The balanceSupplier is obtained from `tokenAccount.getBalance()`.
     * Historical Call:
     * In historical block queries, as the `token_account` and `token_balance` tables lack historical state for NFT balances,
     * the NFT balanceSupplier is retrieved from `NftRepository.nftBalanceByAccountIdTokenIdAndTimestamp`
     */
    private Long getNftBalance(
            final TokenAccount tokenAccount, final Optional<Long> timestamp, long accountCreatedTimestamp) {
        return timestamp
                .map(t -> {
                    if (t >= accountCreatedTimestamp) {
                        return nftRepository.nftBalanceByAccountIdTokenIdAndTimestamp(
                                tokenAccount.getAccountId(), tokenAccount.getTokenId(), t);
                    } else {
                        return ZERO_BALANCE_OPTIONAL;
                    }
                })
                .orElseGet(() -> Optional.of(tokenAccount.getBalance()))
                .orElse(0L);
    }

    /**
     * Fungible Token Balance Explanation:
     * Non-historical Call:
     * The balanceSupplier is obtained from `tokenAccount.getBalance()`.
     * Historical Call:
     * In historical block queries, since the `token_account` table lacks historical state for fungible balances,
     * the fungible balanceSupplier is determined from the `token_balance` table using the `findHistoricalTokenBalanceUpToTimestamp` query.
     * If the entity creation is after the passed timestamp - return 0L (the entity was not created)
     */
    private Long getFungibleBalance(
            final TokenAccount tokenAccount, final Optional<Long> timestamp, long accountCreatedTimestamp) {
        return timestamp
                .map(t -> {
                    if (t >= accountCreatedTimestamp) {
                        return tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                                tokenAccount.getTokenId(), tokenAccount.getAccountId(), t);
                    } else {
                        return ZERO_BALANCE_OPTIONAL;
                    }
                })
                .orElseGet(() -> Optional.of(tokenAccount.getBalance()))
                .orElse(0L);
    }

    private Optional<Entity> findAccount(final AccountID accountID, final Optional<Long> timestamp) {
        return commonEntityAccessor.get(accountID, timestamp);
    }

    private Optional<TokenTypeEnum> findTokenType(final TokenID tokenID) {
        return tokenRepository.findTokenTypeById(toEntityId(tokenID).getId());
    }
}
