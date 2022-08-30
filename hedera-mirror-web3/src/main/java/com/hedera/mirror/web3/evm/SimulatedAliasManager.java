package com.hedera.mirror.web3.evm;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import java.util.Arrays;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.web3.repository.EntityRepository;

@Named
@RequiredArgsConstructor
public class SimulatedAliasManager {

    private static final int EVM_ADDRESS_LEN = 20;
    private static byte[] mirrorPrefix = null;
    private final EntityRepository entityRepository;

    public Address resolveForEvm(final Address addressOrAlias) {
        if (isMirror(addressOrAlias)) {
            return addressOrAlias;
        }

        final var contract = entityRepository.findAccountByPublicKey(addressOrAlias.toUnprefixedHexString()).orElse(null);
        // If we cannot resolve to a mirror address, we return the missing alias and let a
        // downstream component fail the transaction by returning null from its get() method.
        // Cf. the address validator provided by ContractsModule#provideAddressValidator().
        return (contract == null) ? addressOrAlias : Address.wrap(Bytes.wrap(contract.getEvmAddress()));
    }

    private boolean isMirror(final Address address) {
        return isMirror(address.toArrayUnsafe());
    }

    private boolean isMirror(final byte[] address) {
        if (address.length != EVM_ADDRESS_LEN) {
            return false;
        }
        if (mirrorPrefix == null) {
            mirrorPrefix = new byte[12];
            System.arraycopy(0L, 4, mirrorPrefix, 0, 4);
            System.arraycopy(0L, 0, mirrorPrefix, 4, 8);
        }
        return Arrays.equals(mirrorPrefix, 0, 12, address, 0, 12);
    }

//    default Address currentAddress(final ContractID idOrAlias) {
//        if (isAlias(idOrAlias)) {
//            return resolveForEvm(Address.wrap(Bytes.wrap(idOrAlias.getEvmAddress().toByteArray())));
//        }
//        return asTypedEvmAddress(idOrAlias);
//    }
}
