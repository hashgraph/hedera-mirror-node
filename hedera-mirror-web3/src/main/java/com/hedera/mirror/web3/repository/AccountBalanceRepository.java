package com.hedera.mirror.web3.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.hedera.mirror.common.domain.balance.AccountBalance;

public interface AccountBalanceRepository extends CrudRepository<AccountBalance, Long> {

    @Query(value = "select * from account_balance where account_id = (select id from entity where evm_address = ?1) AND consensus_timestamp = ?2", nativeQuery=true)
    Optional<AccountBalance> findByAddressAndConsensusTimestamp(final byte[] evmAddress, final Long consensusTimestamp);

}
