package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.repository.ContractActionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContractActionServiceImpl implements ContractActionService{

    private final ContractActionRepository contractActionRepository;

    @Override
    public List<ContractAction> findAllByConsensusTimestamp(Long consensusTimestamp) {
        return contractActionRepository.findAllByConsensusTimestamp(consensusTimestamp);
    }
}
