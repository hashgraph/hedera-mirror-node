package com.hedera.mirror.web3.service.model;

import lombok.Builder;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.services.evm.store.models.HederaEvmAccount;

@Value
@Builder
public class CallBody {
    //TODO Rename and find appropriate package
    HederaEvmAccount sender;
    Address receiver;
    long providedGasLimit;
    long value;
    Bytes callData;
}
