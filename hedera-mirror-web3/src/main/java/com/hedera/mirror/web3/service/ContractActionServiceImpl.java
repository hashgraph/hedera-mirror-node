package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.contract.ContractTransactionHash;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.repository.ContractActionRepository;
import com.hedera.mirror.web3.service.model.ContractCallDebugServiceParameters;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class ContractActionServiceImpl implements ContractActionService{

    private final ContractActionRepository contractActionRepository;

    @Override
    public List<ContractAction> findFromTransaction(@NonNull @Valid TransactionIdOrHashParameter transactionIdOrHash, ContractCallDebugServiceParameters params) {
        long consensusTimestamp = params.getConsensusTimestamp();
        return contractActionRepository.findAllByConsensusTimestamp(consensusTimestamp);
    }
}
