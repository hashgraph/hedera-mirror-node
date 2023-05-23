/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.utils;

import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asContract;
import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asToken;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static com.hedera.services.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdUtilsTest {

    @Test
    void asSolidityAddressBytesWorksProperly() {
        final var id = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        final var result = asEvmAddress(id);

        final var expectedBytes = new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3};

        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void asSolidityAddressBytesFromToken() {
        final var id = TokenID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setTokenNum(3)
                .build();

        final var result = asEvmAddress(id);

        final var expectedBytes = new byte[] {0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 3};

        assertArrayEquals(expectedBytes, result);
    }

    @Test
    void asContractWorks() {
        final var expected = ContractID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setContractNum(3)
                .build();
        final var id = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        final var cid = asContract(id);

        assertEquals(expected, cid);
    }

    @Test
    void serializesExpectedSolidityAddress() {
        final byte[] shardBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xAB,
        };
        final var shard = Ints.fromByteArray(shardBytes);
        final byte[] realmBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xCD,
            (byte) 0xFE, (byte) 0x00, (byte) 0x00, (byte) 0xFE,
        };
        final var realm = Longs.fromByteArray(realmBytes);
        final byte[] numBytes = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xDE,
            (byte) 0xBA, (byte) 0x00, (byte) 0x00, (byte) 0xBA
        };
        final var num = Longs.fromByteArray(numBytes);
        final byte[] expected = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xAB,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xCD,
            (byte) 0xFE, (byte) 0x00, (byte) 0x00, (byte) 0xFE,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xDE,
            (byte) 0xBA, (byte) 0x00, (byte) 0x00, (byte) 0xBA
        };
        final var create2AddressBytes = Hex.decode("0102030405060708090a0b0c0d0e0f1011121314");
        final var equivAccount = asAccount(String.format("%d.%d.%d", shard, realm, num));
        final var equivContract = asContract(String.format("%d.%d.%d", shard, realm, num));
        final var equivToken = asToken(String.format("%d.%d.%d", shard, realm, num));
        final var create2Contract = ContractID.newBuilder()
                .setEvmAddress(ByteString.copyFrom(create2AddressBytes))
                .build();

        final var actual = asEvmAddress(shard, realm, num);
        final var typedActual = EntityIdUtils.asTypedEvmAddress(equivAccount);
        final var typedToken = EntityIdUtils.asTypedEvmAddress(equivToken);
        final var typedContract = EntityIdUtils.asTypedEvmAddress(equivContract);
        final var anotherActual = asEvmAddress(equivContract);
        final var create2Actual = asEvmAddress(create2Contract);

        assertArrayEquals(expected, actual);
        assertArrayEquals(expected, anotherActual);
        assertArrayEquals(expected, typedActual.toArray());
        assertArrayEquals(expected, typedToken.toArray());
        assertArrayEquals(expected, typedContract.toArray());
        assertArrayEquals(create2AddressBytes, create2Actual);
        assertEquals(equivAccount, EntityIdUtils.accountIdFromEvmAddress(actual));
        assertEquals(equivContract, contractIdFromEvmAddress(actual));
        assertEquals(equivToken, tokenIdFromEvmAddress(actual));
    }

    @ParameterizedTest
    @CsvSource({"1.0.0", "0.1.0", "0.0.1", "1.2.3"})
    void parsesValidLiteral(final String goodLiteral) {
        assertEquals(asAccount(goodLiteral), parseAccount(goodLiteral));
    }

    @ParameterizedTest
    @CsvSource({"asdf", "notANumber"})
    void parsesNonValidLiteral(final String badLiteral) {
        assertThrows(IllegalArgumentException.class, () -> parseAccount(badLiteral));
    }

    @Test
    void entityIdFromId() {
        assertThat(EntityIdUtils.entityIdFromId(new Id(1L, 2L, 3L)))
                .returns(1L, EntityId::getShardNum)
                .returns(2L, EntityId::getRealmNum)
                .returns(3L, EntityId::getEntityNum)
                .returns(EntityType.UNKNOWN, EntityId::getType);
    }

    @Test
    void entityIdFromIdNullHandling() {
        assertThat(EntityIdUtils.entityIdFromId(null)).isNull();
    }

    @Test
    void idFromEntityId() {
        assertThat(EntityIdUtils.idFromEntityId(new EntityId(1L, 2L, 3L, EntityType.ACCOUNT)))
                .returns(1L, Id::shard)
                .returns(2L, Id::realm)
                .returns(3L, Id::num);
    }

    @Test
    void idFromEntityIdNullHandling() {
        assertThat(EntityIdUtils.idFromEntityId(null)).isNull();
    }
}
