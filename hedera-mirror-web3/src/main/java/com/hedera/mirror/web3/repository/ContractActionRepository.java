package com.hedera.mirror.web3.repository;

import com.hedera.mirror.common.domain.contract.ContractAction;
import java.util.List;
import org.springframework.data.repository.CrudRepository;


public interface ContractActionRepository extends CrudRepository<ContractAction, Long>{

    List<ContractAction> findAllByConsensusTimestamp(long consensusTimestamp);
}
