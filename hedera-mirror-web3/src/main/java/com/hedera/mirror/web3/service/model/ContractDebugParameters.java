package com.hedera.mirror.web3.service.model;

import com.hedera.mirror.web3.evm.contracts.execution.traceability.TracerType;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Value
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class ContractDebugParameters implements CallServiceParameters {
    BlockType block;
    Bytes callData;
    CallType callType = CallType.ETH_DEBUG_TRACE_TRANSACTION;
    long consensusTimestamp;
    long gas;
    boolean isEstimate = false;
    boolean isStatic = false;
    Address receiver;
    HederaEvmAccount sender;
    TracerType tracerType = TracerType.OPCODE;
    long value;
}
