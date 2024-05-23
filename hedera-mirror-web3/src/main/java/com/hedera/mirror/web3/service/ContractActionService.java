package com.hedera.mirror.web3.service;

import com.hedera.mirror.common.domain.contract.ContractAction;
import com.hedera.mirror.web3.common.TransactionIdOrHashParameter;
import jakarta.validation.Valid;
import java.util.List;
import lombok.NonNull;

public interface ContractActionService {

    /**
     * @param transactionIdOrHash the transaction ID or hash
     * @return the sidecar contract actions associated with the given transaction
     */
    List<ContractAction> findFromTransaction(@NonNull @Valid TransactionIdOrHashParameter transactionIdOrHash);
}
