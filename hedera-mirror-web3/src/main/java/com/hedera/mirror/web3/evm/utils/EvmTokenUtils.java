package com.hedera.mirror.web3.evm.utils;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toBytes;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import lombok.experimental.UtilityClass;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmKey;

@UtilityClass
public class EvmTokenUtils {
    private static final Address EMPTY_CONTRACT_ADDRESS = Address.wrap(Bytes.wrap(new byte[20]));

    public static Address toAddress(final EntityId entityId) {
        final var bytes = Bytes.wrap(toEvmAddress(entityId));
        return Address.wrap(bytes);
    }

    public static Address toAddress(final ContractID contractID) {
        final var bytes = Bytes.wrap(toEvmAddress(contractID));
        return Address.wrap(bytes);
    }

    public static EvmKey evmKey(final byte[] keyBytes) throws InvalidProtocolBufferException {
        if (keyBytes == null) {
            return new EvmKey();
        }
        var key = Key.parseFrom(keyBytes);
        final var contractId =
                key.hasContractID()
                        ? toAddress(key.getContractID())
                        : EMPTY_CONTRACT_ADDRESS;
        final var ed25519 = key.hasEd25519() ? toBytes(key.getEd25519()) : new byte[0];
        final var ecdsaSecp256K1 = key.hasECDSASecp256K1() ? toBytes(key.getECDSASecp256K1()) : new byte[0];

        final var delegatableContractId =
                key.hasDelegatableContractId()
                        ? toAddress(key.getDelegatableContractId())
                        : EMPTY_CONTRACT_ADDRESS;

        return new EvmKey(contractId, ed25519, ecdsaSecp256K1, delegatableContractId);
    }

    public static Long entityIdNumFromEvmAddress(final Address address) {
        final var id = fromEvmAddress(address.toArrayUnsafe());
        return id != null ? id.getId() : 0;
    }

    public static EntityId entityIdFromEvmAddress(final Address address) {
        return fromEvmAddress(address.toArrayUnsafe());
    }
}
