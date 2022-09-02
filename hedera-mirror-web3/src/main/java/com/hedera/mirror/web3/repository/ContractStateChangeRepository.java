package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.contract.Contract;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import java.util.Optional;

public interface ContractStateChangeRepository extends CrudRepository<Contract, Long> {
    @Query(value = """
                SELECT encode(value_read, 'hex')
                FROM contract_state_change
                INNER JOIN entity
                ON contract_state_change.contract_id = entity.id
                WHERE entity.evm_address = ?1
                AND slot = ?2
                ORDER BY consensus_timestamp DESC
                LIMIT 1""", nativeQuery = true)
    Optional<String> findStorageValue(byte[] id, byte[] key);
}
