package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.contract.ContractAction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;


public interface ContractActionRepository extends CrudRepository<ContractAction, Long>{
    @Query("select r from ContractAction r where r.consensusTimestamp = ?1")
    List<ContractAction> findContractActionByTimestamp(long timestamp);
}
