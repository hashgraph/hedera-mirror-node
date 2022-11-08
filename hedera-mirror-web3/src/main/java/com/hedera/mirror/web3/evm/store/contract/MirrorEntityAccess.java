package com.hedera.mirror.web3.evm.store.contract;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.web3.evm.util.EntityUtils.numFromEvmAddress;

import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.stereotype.Component;

import com.hedera.mirror.web3.repository.EntityAccessRepository;
import com.hedera.services.evm.store.contracts.HederaEvmEntityAccess;

@Component
@RequiredArgsConstructor
public class MirrorEntityAccess implements HederaEvmEntityAccess {
    private final EntityAccessRepository entityRepository;

    @Override
    public long getBalance(Address address) {
        final var entityNum = numFromEvmAddress(address.toArrayUnsafe());
        final var balance = entityRepository.getBalance(entityNum);
        return balance.orElse(0L);
    }

    @Override
    public boolean isExtant(Address address) {
        final var entityNum = numFromEvmAddress(address.toArrayUnsafe());
        return entityRepository.existsById(entityNum);
    }

    @Override
    public boolean isTokenAccount(Address address) {
        final var entityNum = numFromEvmAddress(address.toArrayUnsafe());
        final var type = entityRepository.getType(entityNum);
        return type.isPresent() && type.get().equals(TOKEN);
    }

    @Override
    public ByteString alias(Address address) {
        //TODO implement repo logic here
        final var entityNum = numFromEvmAddress(address.toArrayUnsafe());
        return null;
    }

    @Override
    public UInt256 getStorage(Address address, Bytes key) {
        final var entityNum = numFromEvmAddress(address.toArrayUnsafe());
        final var storage = entityRepository.getStorage(entityNum, key.toArrayUnsafe());
        return UInt256.fromBytes(Bytes.wrap(storage));
    }

    @Override
    public Bytes fetchCodeIfPresent(Address address) {
        final var entityNum = numFromEvmAddress(address.toArrayUnsafe());
        final var runtimeCode = entityRepository.fetchContractCode(entityNum);
        return runtimeCode.map(Bytes::wrap).orElse(Bytes.EMPTY);
    }
}
