package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;

public interface AccountBalanceFileRepository extends CrudRepository<AccountBalanceFile, Long> {

    @Query(value = "select * from account_balance_file order by consensus_timestamp desc limit 1", nativeQuery = true)
    Optional<AccountBalanceFile> findLatest();
}
