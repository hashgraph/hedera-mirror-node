package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.common.domain.transaction.EthereumTransaction;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.repository.ContractActionRepository;
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
    private final TransactionService transactionService;
    private final EthereumTransactionService ethereumTransactionService;

    @Override
    public List<ContractAction> findFromTransaction(@NonNull @Valid TransactionIdOrHashParameter transactionIdOrHash) {
        Assert.isTrue(transactionIdOrHash.isValid(), "Invalid transactionIdOrHash");

        Optional<Long> consensusTimestamp;
        if (transactionIdOrHash.isHash()) {
            consensusTimestamp = ethereumTransactionService
                    .findByHash(transactionIdOrHash.hash().toByteArray())
                    .map(EthereumTransaction::getConsensusTimestamp);
        } else {
            consensusTimestamp = transactionService
                    .findByTransactionId(transactionIdOrHash.transactionID())
                    .map(Transaction::getConsensusTimestamp);
        }

        return consensusTimestamp
                .map(contractActionRepository::findAllByConsensusTimestamp)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found"));
    }
}
