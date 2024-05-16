package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;

import java.util.List;

public interface ContractActionService {

    /**
     * @param consensusTimestamp the consensus timestamp of a record
     * @return the sidecar file associated with a record file
     */
    List<ContractAction> findContractActionByConsensusTimestamp(Long consensusTimestamp);
}
