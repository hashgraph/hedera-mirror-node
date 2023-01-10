package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.balance.TokenBalance;
import com.hedera.mirror.common.domain.balance.TokenBalance.Id;

public interface TokenBalanceRepository extends CrudRepository<TokenBalance, Id> {

    @Query(value = "select balance from token_balance where token_id = ?1 and account_id = ?2",
            nativeQuery = true)
    Optional<Long> findBalance(final Long tokenId, final Long accountId);
}
