package com.hedera.services.transaction.store.contracts;

import java.util.NavigableMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.AccountStorageEntry;

public class WorldStateTokenAccount implements Account {
    public static final String TOKEN_BYTECODE_PATTERN = "fefefefefefefefefefefefefefefefefefefefe";
    public static final String TOKEN_CALL_REDIRECT_CONTRACT_BINARY =
            "6080604052348015600f57600080fd5b506000610167905077618dc65efefefefefefefefefefefefefefefefefefefefe600052366000602037600080366018016008845af43d806000803e8160008114605857816000f35b816000fdfea2646970667358221220d8378feed472ba49a0005514ef7087017f707b45fb9bf56bb81bb93ff19a238b64736f6c634300080b0033";
    public static final long TOKEN_PROXY_ACCOUNT_NONCE = -1;

    private Code interpolatedCode;
    private final Address address;

    public WorldStateTokenAccount(final Address address) {
        this.address = address;
    }

    @Override
    public Bytes getCode() {
        return interpolatedCode().getBytes();
    }

    @Override
    public long getNonce() {
        return TOKEN_PROXY_ACCOUNT_NONCE;
    }

    @Override
    public Address getAddress() {
        return address;
    }

    @Override
    public Wei getBalance() {
        return Wei.ZERO;
    }

    @Override
    public Hash getAddressHash() {
        return Hash.EMPTY;
    }

    @Override
    public Hash getCodeHash() {
        return interpolatedCode().getCodeHash();
    }

    @Override
    public UInt256 getStorageValue(final UInt256 ignoredKey) {
        return UInt256.ZERO;
    }

    @Override
    public UInt256 getOriginalStorageValue(final UInt256 ignoredKey) {
        return UInt256.ZERO;
    }

    @Override
    public NavigableMap<Bytes32, AccountStorageEntry> storageEntriesFrom(Bytes32 bytes32, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasCode() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    private Code interpolatedCode() {
        if (interpolatedCode == null) {
            final var interpolatedBytecode = proxyBytecodeFor(address);
            interpolatedCode = Code.createLegacyCode(interpolatedBytecode, Hash.hash(interpolatedBytecode));
        }
        return interpolatedCode;
    }

    public static Bytes proxyBytecodeFor(final Address address) {
        return Bytes.fromHexString(
                TOKEN_CALL_REDIRECT_CONTRACT_BINARY.replace(
                        TOKEN_BYTECODE_PATTERN, address.toUnprefixedHexString()));
    }
}
