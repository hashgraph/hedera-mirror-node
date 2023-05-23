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
public class TokenRelationshipDatabaseAccessor extends DatabaseAccessor<TokenRelationshipKey, TokenRelationship> {
    private final TokenDatabaseAccessor tokenDatabaseAccessor;
    private final AccountDatabaseAccessor accountDatabaseAccessor;

    private final TokenAccountRepository tokenAccountRepository;

    @Override
    public @NonNull Optional<TokenRelationship> get(@NonNull TokenRelationshipKey key) {
        return findAccount(key.accountAddress())
                .flatMap(account -> findToken(key.tokenAddress()).flatMap(token -> findTokenAccount(token, account)
                        .filter(t -> t.getAssociated() == Boolean.TRUE)
                        .map(tokenAccount -> new TokenRelationship(
                                token,
                                account,
                                tokenAccount.getBalance(),
                                TokenFreezeStatusEnum.FROZEN == tokenAccount.getFreezeStatus(),
                                TokenKycStatusEnum.GRANTED == tokenAccount.getKycStatus(),
                                false,
                                false,
                                Boolean.TRUE == tokenAccount.getAutomaticAssociation(),
                                0))));
    }

    private Optional<Account> findAccount(Address address) {
        return accountDatabaseAccessor.get(address);
    }

    private Optional<Token> findToken(Address address) {
        return tokenDatabaseAccessor.get(address);
    }

    private Optional<TokenAccount> findTokenAccount(Token token, Account account) {
        AbstractTokenAccount.Id id = new AbstractTokenAccount.Id();
        id.setTokenId(EntityIdUtils.entityIdFromId(token.getId()).getId());
        id.setAccountId(EntityIdUtils.entityIdFromId(account.getId()).getId());
        return tokenAccountRepository.findById(id);
    }
}
