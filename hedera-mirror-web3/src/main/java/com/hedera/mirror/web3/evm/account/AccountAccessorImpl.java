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

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.repository.EntityRepository;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

@Named
@RequiredArgsConstructor
public class AccountAccessorImpl implements AccountAccessor {
    public static final int EVM_ADDRESS_SIZE = 20;
    private final MirrorEntityAccess mirrorEntityAccess;
    private final EntityRepository entityRepository;

    @Override
    public Address canonicalAddress(Address addressOrAlias) {
        final var addressBytes = addressOrAlias.toArrayUnsafe();
        if (!isMirror(addressBytes)) {
            final var entityFoundByAlias = entityRepository.findByEvmAddressAndDeletedIsFalse(addressBytes);
            if (entityFoundByAlias.isPresent()) {
                return addressOrAlias;
            }
        }

        return getAddressOrAlias(addressOrAlias);
    }

    @Override
    public boolean isTokenAddress(Address address) {
        return mirrorEntityAccess.isTokenAccount(address);
    }

    public Address getAddressOrAlias(final Address address) {
        final ByteString alias;
        if (!mirrorEntityAccess.isExtant(address)) {
            return address;
        }
        alias = mirrorEntityAccess.alias(address);

        if (!alias.isEmpty() && alias.size() == EVM_ADDRESS_SIZE) {
            return Address.wrap(Bytes.wrap(alias.toByteArray()));
        }
        return address;
    }
}
