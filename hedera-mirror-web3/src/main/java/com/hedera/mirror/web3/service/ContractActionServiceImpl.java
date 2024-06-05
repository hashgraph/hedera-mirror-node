package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.repository.ContractActionRepository;
import com.hedera.mirror.web3.service.model.ContractDebugParameters;
import jakarta.validation.Valid;
import java.util.List;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContractActionServiceImpl implements ContractActionService {

    private final ContractActionRepository contractActionRepository;

    @Override
    public List<ContractAction> findFromTransaction(@NonNull @Valid TransactionIdOrHashParameter transactionIdOrHash, ContractDebugParameters params) {
        long consensusTimestamp = params.getConsensusTimestamp();
        return contractActionRepository.findAllByConsensusTimestamp(consensusTimestamp);
    }
}
