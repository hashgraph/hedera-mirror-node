package com.hedera.mirror.web3.service.eth;

import com.google.protobuf.ByteString;
import lombok.Value;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.services.transaction.ethereum.EthTxSigs;
import com.hedera.services.transaction.models.Id;

@Value
public class AccountDto {

    private static final int EVM_ADDRESS_SIZE = 20;
    private static final int ECDSA_SECP256K1_ALIAS_SIZE = 35;
    private static final ByteString ECDSA_KEY_ALIAS_PREFIX = ByteString.copyFrom(new byte[] { 0x3a, 0x21 });

    Long num;
    ByteString alias;

    public Address canonicalAddress() {
        if (alias.isEmpty()) {
            return new Id(0,0,num).asEvmAddress();
        } else {
            if (alias.size() == EVM_ADDRESS_SIZE) {
                return Address.wrap(Bytes.wrap(alias.toByteArray()));
            } else if (alias.size() == ECDSA_SECP256K1_ALIAS_SIZE && alias.startsWith(ECDSA_KEY_ALIAS_PREFIX)) {
                var addressBytes = EthTxSigs.recoverAddressFromPubKey(alias.substring(2).toByteArray());
                return addressBytes == null ? new Id(0,0,num).asEvmAddress() : Address.wrap(Bytes.wrap(addressBytes));
            } else {
                return new Id(0,0,num).asEvmAddress();
            }
        }
    }
}
