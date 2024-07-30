package com.hedera.mirror.restjava.repository;

import com.hedera.mirror.common.domain.token.AbstractTokenAccount.Id;
import com.hedera.mirror.common.domain.token.TokenAccount;
import org.springframework.data.repository.CrudRepository;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, Id> {
}
