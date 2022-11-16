package com.hedera.mirror.web3.evm.store.models;

import lombok.Value;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.services.evm.store.models.HederaEvmAccount;

@Value
public class MirrorEvmAccount implements HederaEvmAccount {

    Address address;

    @Override
    public Address canonicalAddress() {
        return address;
    }
}
