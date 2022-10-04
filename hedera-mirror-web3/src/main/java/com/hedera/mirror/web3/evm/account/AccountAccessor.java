package com.hedera.mirror.web3.evm.account;

import com.google.protobuf.ByteString;
import org.hyperledger.besu.datatypes.Address;

public interface AccountAccessor {
    boolean isTokenTreasury(final Address addressOrAlias);

    boolean hasAnyBalance(final Address addressOrAlias);

    boolean ownsNfts(final Address addressOrAlias);

    boolean isTokenAddress(final Address address);

    ByteString getAlias(final Address address);
}
