package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.web3.evm.util.EntityUtils.accountIdFromEvmAddress;

import com.hederahashgraph.api.proto.java.AccountID;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.stereotype.Component;

import com.hedera.mirror.web3.evm.EntityAccess;
import com.hedera.mirror.web3.repository.EntityAccessRepository;

@Component
@RequiredArgsConstructor
public class MirrorEntityAccess implements EntityAccess {
    private final EntityAccessRepository entityRepository;

    @Override
    public long getBalance(AccountID id) {
        final var balance = entityRepository.getBalance
                (id.getRealmNum(), id.getShardNum(), id.getAccountNum());
        return balance.orElse(0L);
    }

    @Override
    public boolean isDeleted(Address address) {
        final var accountID = accountIdFromEvmAddress(address);
        final var realm = accountID.getRealmNum();
        final var shard = accountID.getShardNum();
        final var accountNum = accountID.getAccountNum();

        return entityRepository.isDeleted(realm, shard, accountNum).orElse(true);
    }

    @Override
    public boolean isExtant(AccountID id) {
        return entityRepository.existsById(id.getAccountNum());
    }

    @Override
    public boolean isTokenAccount(Address address) {
        final var account = accountIdFromEvmAddress(address);
        final var type = entityRepository.getType(account.getRealmNum(), account.getShardNum(),
                account.getAccountNum());
        return type.isPresent() && type.get().equals(TOKEN);
    }


    @Override
    public UInt256 getStorage(AccountID id, UInt256 key) {
        final var storage = entityRepository.getStorage(id.getAccountNum(), key.toArrayUnsafe());
        return UInt256.fromBytes(Bytes.wrap(storage));
    }


    @Override
    public Bytes fetchCodeIfPresent(AccountID id) {
        final var runtimeCode = entityRepository.fetchContractCode(id.getAccountNum());
        return runtimeCode.map(Bytes::wrap).orElse(Bytes.EMPTY);
    }
}
