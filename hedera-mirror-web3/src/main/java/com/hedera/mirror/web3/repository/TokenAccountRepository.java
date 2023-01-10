package com.hedera.mirror.web3.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.token.AbstractTokenAccount.Id;
import com.hedera.mirror.common.domain.token.TokenAccount;

public interface TokenAccountRepository extends CrudRepository<TokenAccount, Id> {

    @Query(value = "select freeze_status from token_account where account_id = ?1 and token_id = ?2",
            nativeQuery = true)
    boolean findFrozenStatus(final Long accountId, final Long tokenId);

    @Query(value = "select kyc_status from token_account where account_id = ?1 and token_id = ?2",
            nativeQuery = true)
    boolean findKycStatus(final Long accountId, final Long tokenId);
}
