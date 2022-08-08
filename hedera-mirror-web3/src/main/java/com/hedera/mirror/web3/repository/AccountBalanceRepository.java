package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.balance.AccountBalance;

public interface AccountBalanceRepository extends CrudRepository<AccountBalance, Long> {

    @Query(value = "select * from account_balance where account_id = ?1 order by consensus_timestamp desc limit 1", nativeQuery=true)
    Optional<AccountBalance> findByAccountId(final long accountId);

}
