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
    public static Address toAddress(EntityId contractID) {
        final var bytes = Bytes.wrap(toEvmAddress(contractID));
        return Address.wrap(bytes);
    }

    public static Address toAddress(ContractID contractID) {
        final var bytes = Bytes.wrap(toEvmAddress(contractID));
        return Address.wrap(bytes);
    }

    public static EvmKey evmKey(byte[] keyBytes) throws InvalidProtocolBufferException {
        if(keyBytes == null){
            return new EvmKey();
        }
        var key = Key.parseFrom(keyBytes);
        final var contractId =
                key.getContractID().getContractNum() > 0
                        ? toAddress(key.getContractID())
                        : emptyContractAddress();
        final var ed25519 = key.getEd25519().toByteArray();
        final var ecdsaSecp256K1 = key.getECDSASecp256K1().toByteArray();
        final var delegatableContractId =
                key.getDelegatableContractId().getContractNum() > 0
                        ? toAddress(key.getDelegatableContractId())
                        : emptyContractAddress();

        return new EvmKey(contractId, ed25519, ecdsaSecp256K1, delegatableContractId);
    }

    private Address emptyContractAddress() {
        return Address.wrap(Bytes.wrap(new byte[20]));
    }

    public static Long entityIdFromEvmAddress(final Address address) {
        final var id = fromEvmAddress(address.toArrayUnsafe());
        return id.getId();
    }
}
