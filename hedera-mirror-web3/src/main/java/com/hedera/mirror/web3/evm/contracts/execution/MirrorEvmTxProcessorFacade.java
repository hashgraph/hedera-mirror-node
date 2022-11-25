package com.hedera.mirror.web3.evm.contracts.execution;

import com.hedera.services.evm.contracts.execution.HederaEvmTransactionProcessingResult;
import com.hedera.services.evm.store.models.HederaEvmAccount;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public interface MirrorEvmTxProcessorFacade {
    HederaEvmTransactionProcessingResult execute(
            final HederaEvmAccount sender,
            final Address receiver,
            final long providedGasLimit,
            final long value,
            final Bytes callData);
}
