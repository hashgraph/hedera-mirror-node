package com.hedera.mirror.web3.service.model;

import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Value
@Builder
@RequiredArgsConstructor
public class ContractCallDebugServiceParameters implements BaseCallServiceParameters {
    HederaEvmAccount sender;
    Address receiver;
    long gas;
    long value;
    Bytes callData;
    BlockType block;
    long consensusTimestamp;
    boolean isStatic = false;
    boolean isEstimate = false;
    CallType callType = CallType.ETH_DEBUG_TRACE_TRANSACTION;
}
