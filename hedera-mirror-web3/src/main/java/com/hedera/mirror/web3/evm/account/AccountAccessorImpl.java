package com.hedera.mirror.web3.evm.account;

import com.hedera.services.evm.accounts.AccountAccessor;

import org.hyperledger.besu.datatypes.Address;

public class AccountAccessorImpl implements AccountAccessor {

    @Override
    public Address canonicalAddress(Address addressOrAlias) {
        return null;
    }

    @Override
    public boolean isTokenAddress(Address address) {
        return false;
    }
}
