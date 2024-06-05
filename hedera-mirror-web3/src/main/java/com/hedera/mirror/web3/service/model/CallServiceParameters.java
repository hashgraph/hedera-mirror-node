package com.hedera.mirror.web3.service.model;


import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public interface CallServiceParameters {
    BlockType getBlock();
    Bytes getCallData();
    CallType getCallType();
    long getGas();
    Address getReceiver();
    HederaEvmAccount getSender();
    boolean isEstimate();
    boolean isStatic();
    long getValue();

    public enum CallType {
        ETH_CALL,
        ETH_DEBUG_TRACE_TRANSACTION,
        ETH_ESTIMATE_GAS,
        ERROR
    }
}
