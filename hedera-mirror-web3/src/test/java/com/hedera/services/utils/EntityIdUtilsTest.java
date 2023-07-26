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

import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.isOfEcdsaPublicAddressSize;
import static com.hedera.services.utils.EntityIdUtils.isOfEvmAddressSize;
import static com.hedera.services.utils.EntityIdUtils.parseAccount;
import static com.hedera.services.utils.EntityIdUtils.tokenIdFromEvmAddress;
import static com.hedera.services.utils.IdUtils.asAccount;
import static com.hedera.services.utils.IdUtils.asContract;
import static com.hedera.services.utils.IdUtils.asToken;
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
import com.swirlds.common.utility.CommonUtils;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityIdUtilsTest {

    public static final ByteString ECDSA_PUBLIC_KEY =
            ByteString.fromHex("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d");
    public static final ByteString ECDSA_WRONG_PUBLIC_KEY =
            ByteString.fromHex("3a2103af80b90d145da28c583359beb47b217511e443e7a64dfdb27d");
    public static final ByteString EVM_ADDRESS = ByteString.fromHex("ebb9a1be370150759408cd7af48e9eda2b8ead57");
    public static final ByteString WRONG_EVM_ADDRESS = ByteString.fromHex("ebb9a1be3701cd7af48e9eda2b8ead57");

    @Test
    void asSolidityAddressBytesWorksProperly() {
        final var id = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        final var result = asEvmAddress(id);

        final var expectedBytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3};

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

        final var expectedBytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3};

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
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
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

        final var actual = asEvmAddress(num);
        final var typedActual = EntityIdUtils.asTypedEvmAddress(equivAccount);
        final var typedToken = EntityIdUtils.asTypedEvmAddress(equivToken);
        final var anotherActual = EntityIdUtils.asEvmAddress(equivContract);
        final var create2Actual = EntityIdUtils.asEvmAddress(create2Contract);
        final var actualHex = EntityIdUtils.asHexedEvmAddress(equivAccount);

        assertArrayEquals(expected, actual);
        assertArrayEquals(expected, anotherActual);
        assertArrayEquals(expected, typedActual.toArray());
        assertArrayEquals(expected, typedToken.toArray());
        assertArrayEquals(create2AddressBytes, create2Actual);
        assertEquals(CommonUtils.hex(expected), actualHex);
        assertEquals(asAccount(String.format("%d.%d.%d", 0, 0, num)), EntityIdUtils.accountIdFromEvmAddress(actual));
        assertEquals(asContract(String.format("%d.%d.%d", 0, 0, num)), contractIdFromEvmAddress(actual));
        assertEquals(asToken(String.format("%d.%d.%d", 0, 0, num)), tokenIdFromEvmAddress(actual));
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

    @Test
    void isOfEvmAddressSizeWorks() {
        assertThat(isOfEvmAddressSize(EVM_ADDRESS)).isTrue();
        assertThat(isOfEvmAddressSize(WRONG_EVM_ADDRESS)).isFalse();
    }

    @Test
    void isOfEcdsaPublicAddressSizeWorks() {
        assertThat(isOfEcdsaPublicAddressSize(ECDSA_PUBLIC_KEY)).isTrue();
        assertThat(isOfEcdsaPublicAddressSize(ECDSA_WRONG_PUBLIC_KEY)).isFalse();
    }

    @Test
    void asSolidityAddressHexWorksProperly() {
        final var id = new Id(1, 2, 3);

        assertEquals("0000000000000000000000000000000000000003", EntityIdUtils.asHexedEvmAddress(id));
    }

    @Test
    void asSolidityAddressHexWorksProperlyForAccount() {
        final var accountId = AccountID.newBuilder()
                .setShardNum(1)
                .setRealmNum(2)
                .setAccountNum(3)
                .build();

        assertEquals("0000000000000000000000000000000000000003", EntityIdUtils.asHexedEvmAddress(accountId));
    }
}
