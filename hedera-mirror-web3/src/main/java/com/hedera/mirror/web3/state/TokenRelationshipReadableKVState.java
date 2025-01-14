/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Optional;
import java.util.function.Supplier;

@SuppressWarnings("deprecation")
@Named
public class TokenRelationshipReadableKVState extends AbstractReadableKVState<EntityIDPair, TokenRelation> {

    public static final String KEY = "TOKEN_RELS";
    private final NftRepository nftRepository;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;
    private final TokenRepository tokenRepository;

    protected TokenRelationshipReadableKVState(
            final NftRepository nftRepository,
            final TokenAccountRepository tokenAccountRepository,
            final TokenBalanceRepository tokenBalanceRepository,
            final TokenRepository tokenRepository) {
        super(KEY);
        this.nftRepository = nftRepository;
        this.tokenAccountRepository = tokenAccountRepository;
        this.tokenBalanceRepository = tokenBalanceRepository;
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected TokenRelation readFromDataSource(@Nonnull EntityIDPair key) {
        final var tokenId = key.tokenId();
        final var accountId = key.accountId();
        if (tokenId == null
                || accountId == null
                || AccountID.DEFAULT.equals(accountId)
                || TokenID.DEFAULT.equals(tokenId)) {
            return null;
        }

        final var timestamp = ContractCallContext.get().getTimestamp();
        // The accountId will always be in the format "shard.realm.num"
        return findTokenAccount(tokenId, accountId, timestamp)
                .map(ta -> tokenRelationFromEntity(tokenId, accountId, ta, timestamp))
                .orElse(null);
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
     * For the latest block we have the balance directly as a field in the TokenAccount object. For the historical block
     * we need to execute a query to calculate the historical balance, but we first need to find the account created
     * timestamp and the token type in order to use the correct repository.
     */
    private Supplier<Long> getBalance(final TokenAccount tokenAccount, final Optional<Long> timestamp) {
        return Suppliers.memoize(() -> timestamp
                .map(t -> findTokenType(tokenAccount.getTokenId())
                        .map(tokenTypeEnum -> tokenTypeEnum.equals(TokenTypeEnum.NON_FUNGIBLE_UNIQUE)
                                ? getNftBalance(tokenAccount, t)
                                : getFungibleBalance(tokenAccount, t))
                        .orElse(0L))
                .orElseGet(tokenAccount::getBalance));
    }

    private Long getNftBalance(final TokenAccount tokenAccount, final long timestamp) {
        return nftRepository
                .nftBalanceByAccountIdTokenIdAndTimestamp(
                        tokenAccount.getTokenId(), tokenAccount.getAccountId(), timestamp)
                .orElse(0L);
    }

    private Long getFungibleBalance(final TokenAccount tokenAccount, final long timestamp) {
        return tokenBalanceRepository
                .findHistoricalTokenBalanceUpToTimestamp(
                        tokenAccount.getTokenId(), tokenAccount.getAccountId(), timestamp)
                .orElse(0L);
    }

    private Optional<TokenTypeEnum> findTokenType(final long tokenId) {
        return tokenRepository.findTypeByTokenId(tokenId);
    }
}
