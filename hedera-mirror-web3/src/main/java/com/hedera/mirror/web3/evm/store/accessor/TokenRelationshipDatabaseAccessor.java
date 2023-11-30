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
                                            tokenAccount.getBalance(),
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
