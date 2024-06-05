package com.hedera.mirror.web3.service.model;


import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public interface CallServiceParameters {
    HederaEvmAccount getSender();
    Address getReceiver();
    long getGas();
    long getValue();
    Bytes getCallData();
    boolean isStatic();
    boolean isEstimate();
    CallType getCallType();
    BlockType getBlock();

    enum CallType {
        ETH_CALL,
        ETH_DEBUG_TRACE_TRANSACTION,
        ETH_ESTIMATE_GAS,
        ERROR
    }
}
