package com.hedera.mirror.web3.evm.account;

import com.google.protobuf.ByteString;

import java.math.BigInteger;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.stereotype.Component;

import com.hedera.mirror.web3.repository.AccountRepository;

import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.web3.evm.util.AccountUtil.accountIdFromEvmAddress;

@Component
@RequiredArgsConstructor
public class AccountAccessorImpl implements AccountAccessor {
    private final AccountRepository accountRepository;

    @Override
    public boolean isTokenTreasury(Address addressOrAlias) {
        final var accountID = accountIdFromEvmAddress(addressOrAlias.toArrayUnsafe());
        final var accountNum = BigInteger.valueOf(accountID.getAccountNum());
        return accountRepository.isTokenTreasury(accountNum).orElse(false);
    }

    @Override
    public boolean hasAnyBalance(Address addressOrAlias) {
        final var accountID = accountIdFromEvmAddress(addressOrAlias.toArrayUnsafe());
        final var realm = BigInteger.valueOf(accountID.getRealmNum());
        final var shard = BigInteger.valueOf(accountID.getShardNum());
        final var accountNum = BigInteger.valueOf(accountID.getAccountNum());

        final var accountBalance = accountRepository.getBalance(realm, shard, accountNum);
        return accountBalance.isPresent() && accountBalance.get().signum() > 0;
    }

    @Override
    public boolean ownsNfts(Address addressOrAlias) {
        final var accountID = accountIdFromEvmAddress(addressOrAlias.toArrayUnsafe());
        final var accountNum = BigInteger.valueOf(accountID.getAccountNum());
        return accountRepository.ownsNfts(accountNum).orElse(false);
    }

    @Override
    public boolean isTokenAddress(Address address) {
        final var accountID = accountIdFromEvmAddress(address.toArrayUnsafe());
        final var realm = BigInteger.valueOf(accountID.getRealmNum());
        final var shard = BigInteger.valueOf(accountID.getShardNum());
        final var accountNum = BigInteger.valueOf(accountID.getAccountNum());

        final var type = accountRepository.getType(realm, shard, accountNum);
        return type.isPresent() && type.get().equals(TOKEN);
    }

    @Override
    public ByteString getAlias(Address address) {
        final var accountID = accountIdFromEvmAddress(address.toArrayUnsafe());
        final var realm = BigInteger.valueOf(accountID.getRealmNum());
        final var shard = BigInteger.valueOf(accountID.getShardNum());
        final var accountNum = BigInteger.valueOf(accountID.getAccountNum());

        final var accountAlias = accountRepository.getAlias(realm, shard, accountNum);
        return accountAlias != null ? ByteString.copyFrom(accountAlias) : ByteString.EMPTY;
    }
}
