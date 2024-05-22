package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import java.util.List;

public interface ContractActionService {

    /**
     * @param consensusTimestamp the consensus timestamp of a record
     * @return the sidecar contract actions associated with the given consensus timestamp
     */
    List<ContractAction> findAllByConsensusTimestamp(Long consensusTimestamp);
}
