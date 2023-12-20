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

package com.hedera.mirror.web3.evm.store.accessor;

import com.hedera.mirror.common.domain.token.AbstractTokenAccount;
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenFreezeStatusEnum;
import com.hedera.mirror.common.domain.token.TokenKycStatusEnum;
import com.hedera.mirror.web3.evm.store.DatabaseBackedStateFrame.DatabaseAccessIncorrectKeyTypeException;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.repository.TokenAccountRepository;
import com.hedera.mirror.web3.repository.TokenBalanceRepository;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.EntityIdUtils;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;

@Named
@RequiredArgsConstructor
public class TokenRelationshipDatabaseAccessor extends DatabaseAccessor<Object, TokenRelationship> {
    private final TokenDatabaseAccessor tokenDatabaseAccessor;
    private final AccountDatabaseAccessor accountDatabaseAccessor;
    private final TokenAccountRepository tokenAccountRepository;
    private final TokenBalanceRepository tokenBalanceRepository;

    @Override
    public @NonNull Optional<TokenRelationship> get(@NonNull Object key, final Optional<Long> timestamp) {
        if (key instanceof TokenRelationshipKey tokenRelationshipKey) {
            return findAccount(tokenRelationshipKey.accountAddress(), timestamp)
                    .flatMap(account -> findToken(tokenRelationshipKey.tokenAddress(), timestamp)
                            .flatMap(token -> findTokenAccount(token, account, timestamp)
                                    .filter(AbstractTokenAccount::getAssociated)
                                    .map(tokenAccount -> new TokenRelationship(
                                            token,
                                            account,
                                            getBalance(account, token, tokenAccount, timestamp),
                                            TokenFreezeStatusEnum.FROZEN == tokenAccount.getFreezeStatus(),
                                            TokenKycStatusEnum.REVOKED != tokenAccount.getKycStatus(),
                                            false,
                                            false,
                                            Boolean.TRUE == tokenAccount.getAutomaticAssociation(),
                                            0))));
        }
        throw new DatabaseAccessIncorrectKeyTypeException("Accessor for class %s failed to fetch by key of type %s"
                .formatted(TokenRelationship.class.getTypeName(), key.getClass().getTypeName()));
    }

    /**
     * Determines fungible or NFT balance based on block context.
     *
     * NFT Balance Explanation:
     *
     * Non-historical Call:
     * When querying the latest block, the NFT balance from `account.getOwnedNfts()`
     * matches the balance from `tokenAccount.getBalance()`.
     * Therefore, the NFT balance is obtained from `account.getOwnedNfts()`.
     *
     * Historical Call:
     * In historical block queries, as the `token_account` and `token_balance` tables lack historical state for NFT balances,
     * the NFT balance is retrieved from `account.getOwnedNfts()`, previously set in `AccountDatabaseAccessor.getOwnedNfts()`.
     *
     * Fungible Token Balance Explanation:
     *
     * Non-historical Call:
     * The same principle as NFT applies here, and the balance is obtained from `account.getOwnedNfts()`.
     *
     * Historical Call:
     * In historical block queries, since the `token_account` table lacks historical state for fungible balances,
     * the balance is determined from the `token_balance` table using the `findHistoricalTokenBalanceUpToTimestamp` query.
     */
    private Long getBalance(Account account, Token token, TokenAccount tokenAccount, final Optional<Long> timestamp) {
        if (token.getType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            return account.getOwnedNfts();
        }
        return timestamp
                .map(t -> tokenBalanceRepository.findHistoricalTokenBalanceUpToTimestamp(
                        tokenAccount.getTokenId(), tokenAccount.getAccountId(), t, account.getCreatedTimestamp()))
                .orElseGet(() -> Optional.of(tokenAccount.getBalance()))
                .orElse(0L);
    }

    private Optional<Account> findAccount(Address address, final Optional<Long> timestamp) {
        return accountDatabaseAccessor.get(address, timestamp);
    }

    private Optional<Token> findToken(Address address, final Optional<Long> timestamp) {
        return tokenDatabaseAccessor.get(address, timestamp);
    }

    private Optional<TokenAccount> findTokenAccount(Token token, Account account, final Optional<Long> timestamp) {
        AbstractTokenAccount.Id id = new AbstractTokenAccount.Id();
        id.setTokenId(EntityIdUtils.entityIdFromId(token.getId()).getId());
        id.setAccountId(EntityIdUtils.entityIdFromId(account.getId()).getId());
        return timestamp
                .map(t -> tokenAccountRepository.findByIdAndTimestamp(id.getAccountId(), id.getTokenId(), t))
                .orElseGet(() -> tokenAccountRepository.findById(id));
    }
}
