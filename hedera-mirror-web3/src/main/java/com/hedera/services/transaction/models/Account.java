package com.hedera.services.transaction.models;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.services.transaction.ethereum.EthTxSigs;

/**
 * Encapsulates the state and operations of a Hedera account.
 * <p>
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 *
 * <b>NOTE:</b> This implementation is incomplete, and includes
 * only the API needed to support the Hedera Token Service. The
 * memo field, for example, is not yet present.
 */
public class Account {
    private static final int EVM_ADDRESS_SIZE = 20;
    private static final int ECDSA_SECP256K1_ALIAS_SIZE = 35;
    private static final ByteString ECDSA_KEY_ALIAS_PREFIX = ByteString.copyFrom(new byte[] { 0x3a, 0x21 });

    private final Id id;
    private ByteString alias = ByteString.EMPTY;

    public Account(Id id) {
        this.id = id;
    }

    public Address canonicalAddress() {
        if (alias.isEmpty()) {
            return id.asEvmAddress();
        } else {
            if (alias.size() == EVM_ADDRESS_SIZE) {
                return Address.wrap(Bytes.wrap(alias.toByteArray()));
            } else if (alias.size() == ECDSA_SECP256K1_ALIAS_SIZE && alias.startsWith(ECDSA_KEY_ALIAS_PREFIX)) {
                var addressBytes = EthTxSigs.recoverAddressFromPubKey(alias.substring(2).toByteArray());
                return addressBytes == null ? id.asEvmAddress() : Address.wrap(Bytes.wrap(addressBytes));
            } else {
                return id.asEvmAddress();
            }
        }
    }

    public Id getId() {
        return id;
    }
}
