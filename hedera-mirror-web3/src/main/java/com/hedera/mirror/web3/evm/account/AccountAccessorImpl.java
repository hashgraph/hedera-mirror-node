/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.mirror.web3.evm.account;

import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import lombok.RequiredArgsConstructor;
import org.hyperledger.besu.datatypes.Address;

@RequiredArgsConstructor
public class AccountAccessorImpl implements AccountAccessor {
    public static final int EVM_ADDRESS_SIZE = 20;

    private final Store store;
    private final HederaEvmEntityAccess mirrorEntityAccess;
    private final MirrorEvmContractAliases aliases;

    @Override
    public Address canonicalAddress(Address addressOrAlias) {
        if (aliases.isInUse(addressOrAlias)) {
            return addressOrAlias;
        }

        return getAddressOrAlias(addressOrAlias);
    }

    @Override
    public boolean isTokenAddress(Address address) {
        return mirrorEntityAccess.isTokenAccount(address);
    }

    public Address getAddressOrAlias(final Address address) {
        if (mirrorEntityAccess.isExtant(address)) {
            return address;
        }
        // An EIP-1014 address is always canonical
        if (!aliases.isMirror(address)) {
            return address;
        }

        final var account = store.getAccount(address, OnMissing.DONT_THROW);
        return account.canonicalAddress();
    }
}
