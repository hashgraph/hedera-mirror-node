package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.repository.ContractActionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractActionServiceImpl implements ContractActionService{
    private final ContractActionRepository contractActionRepository;
    @Override
    public List<ContractAction> findContractActionByConsensusTimestamp(Long consensusTimestamp) {
        return contractActionRepository.findContractActionByTimestamp(consensusTimestamp);
    }
}
