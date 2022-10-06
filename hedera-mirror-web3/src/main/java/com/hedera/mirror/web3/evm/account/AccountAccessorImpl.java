package com.hedera.mirror.web3.evm.account;

import static com.google.protobuf.ByteString.*;
import static com.hedera.mirror.common.domain.entity.EntityType.TOKEN;
import static com.hedera.mirror.common.domain.entity.EntityType.UNKNOWN;
import static com.hedera.mirror.web3.evm.util.AccountUtil.accountIdFromEvmAddress;

import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.stereotype.Component;

import com.hedera.mirror.web3.repository.AccountRepository;

@Component
@RequiredArgsConstructor
public class AccountAccessorImpl implements AccountAccessor {
    private final AccountRepository accountRepository;

    @Override
    public boolean isTokenTreasury(Address addressOrAlias) {
        final var accountID = accountIdFromEvmAddress(addressOrAlias);
        return accountRepository.isTokenTreasury(accountID.getAccountNum()).orElse(false);
    }

    @Override
    public boolean hasAnyBalance(Address addressOrAlias) {
        final var accountID = accountIdFromEvmAddress(addressOrAlias);
        final var accountBalance = accountRepository.getBalance
                        (accountID.getRealmNum(), accountID.getShardNum(), accountID.getAccountNum())
                .orElse(0L);

        return accountBalance > 0;
    }

    @Override
    public boolean ownsNfts(Address addressOrAlias) {
        final var accountID = accountIdFromEvmAddress(addressOrAlias);
        return accountRepository.ownsNfts(accountID.getAccountNum()).orElse(false);
    }

    @Override
    public boolean isTokenAddress(Address address) {
        final var accountID = accountIdFromEvmAddress(address);
        final var type = accountRepository.getType
                        (accountID.getRealmNum(), accountID.getShardNum(), accountID.getAccountNum())
                .orElse(UNKNOWN);

        return type.equals(TOKEN);
    }

    @Override
    public ByteString getAlias(Address address) {
        final var accountID = accountIdFromEvmAddress(address);
        final var accountAlias = accountRepository.getAlias
                (accountID.getRealmNum(), accountID.getShardNum(), accountID.getAccountNum());

        return accountAlias != null ? copyFrom(accountAlias) : EMPTY;
    }
}
