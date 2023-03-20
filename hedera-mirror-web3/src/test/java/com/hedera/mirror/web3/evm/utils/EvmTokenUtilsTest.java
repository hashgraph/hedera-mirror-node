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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ContractID;
import org.apache.commons.codec.DecoderException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.commons.codec.binary.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;

class EvmTokenUtilsTest {
    private static final Address EMPTY_ADDRESS = Address.wrap(Bytes.wrap(new byte[20]));
    @Test
    void toAddress() {
        long shard = 1;
        long realm = 2;
        long num = 120;
        long accountNum = 220;
        ContractID contractId = ContractID.newBuilder().setShardNum(shard).setRealmNum(realm).setContractNum(num)
                .build();
        byte[] contractAddress = new byte[20];
        contractAddress[3] = (byte) shard;
        contractAddress[11] = (byte) realm;
        contractAddress[19] = (byte) num;

        assertThat(EvmTokenUtils.toAddress(contractId).toArrayUnsafe()).isEqualTo(contractAddress);

        EntityId accountId = EntityId.of(shard, realm, accountNum, EntityType.ACCOUNT);
        byte[] accountAddress = new byte[20];
        accountAddress[3] = (byte) shard;
        accountAddress[11] = (byte) realm;
        accountAddress[19] = (byte) accountNum;

        assertThat(EvmTokenUtils.toAddress(accountId).toArrayUnsafe()).isEqualTo(accountAddress);
    }

    @Test
    void evmKeyWithEcdsa() throws InvalidProtocolBufferException, DecoderException {
        //hexed value of a serialized Key with ecdsa algorithm
        final var ecdsaBytes = Hex.decodeHex("3a21ccd4f651636406f8a2a9902a2a604be1fb480dba6591ff4d992f8a6bc6abc137c7");

        final var result = EvmTokenUtils.evmKey(ecdsaBytes);

        assertThat(result.getECDSASecp256K1()).isNotEmpty();
        assertThat(result.getEd25519()).isEmpty();
        assertThat(result.getContractId()).isEqualTo(EMPTY_ADDRESS);
        assertThat(result.getDelegatableContractId()).isEqualTo(EMPTY_ADDRESS);
    }

    @Test
    void evmKeyWithEd25519() throws InvalidProtocolBufferException, DecoderException {
        //hexed value of a serialized Key with ed25519 algorithm
        final var keyWithEd25519 = Hex.decodeHex("1220000038746a20d630ceb81a24bd43798159108ec144e185c1c60a5e39fb933e2a");

        final var result = EvmTokenUtils.evmKey(keyWithEd25519);

        assertThat(result.getECDSASecp256K1()).isEmpty();
        assertThat(result.getEd25519()).isNotEmpty();
        assertThat(result.getContractId()).isEqualTo(EMPTY_ADDRESS);
        assertThat(result.getDelegatableContractId()).isEqualTo(EMPTY_ADDRESS);
    }

    @Test
    void evmKeyWithContractId() throws InvalidProtocolBufferException, DecoderException {
        //hexed value of a serialized Key with contractId
        final var keyWithContractId = Hex.decodeHex("0a070801100118c409");
        final var contractAddress = Address.fromHexString("0x00000001000000000000000100000000000004c4");

        final var result = EvmTokenUtils.evmKey(keyWithContractId);

        assertThat(result.getECDSASecp256K1()).isEmpty();
        assertThat(result.getEd25519()).isEmpty();
        assertThat(result.getContractId()).isEqualTo(contractAddress);
        assertThat(result.getDelegatableContractId()).isEqualTo(EMPTY_ADDRESS);
    }

    @Test
    void evmKeyWithDelegateContractId() throws InvalidProtocolBufferException, DecoderException {
        //hexed value of a serialized Key with delegate contractId
        final var keyWithDelegateContractId = Hex.decodeHex("420318c509");
        final var delegateContractAddress = Address.fromHexString("0x00000000000000000000000000000000000004c5");

        final var result = EvmTokenUtils.evmKey(keyWithDelegateContractId);

        assertThat(result.getECDSASecp256K1()).isEmpty();
        assertThat(result.getEd25519()).isEmpty();
        assertThat(result.getDelegatableContractId()).isEqualTo(delegateContractAddress);
        assertThat(result.getContractId()).isEqualTo(EMPTY_ADDRESS);
    }

    @Test
    void emptyEvmKeyForNull() throws InvalidProtocolBufferException {
        final var result = EvmTokenUtils.evmKey(null);

        assertThat(result.getEd25519()).isEmpty();
        assertThat(result.getECDSASecp256K1()).isEmpty();
        assertThat(result.getContractId()).isEqualTo(Address.ZERO);
        assertThat(result.getDelegatableContractId()).isEqualTo(Address.ZERO);
    }

    @Test
    void entityIdFromAddress(){
        final var contractAddress = Address.fromHexString("0x00000000000000000000000000000000000004c5");

        assertThat(EvmTokenUtils.entityIdNumFromEvmAddress(contractAddress)).isEqualTo(1221);
    }
}
