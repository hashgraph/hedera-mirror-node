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

import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.jproto.JKey;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MirrorEvmContractAliasesTest {

    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);
    private static final String HEX2 = "0x00000000000000000000000000000000000004e5";
    private static final Address ADDRESS2 = Address.fromHexString(HEX2);
    private static final String INVALID_HEX_ADDRESS = "0x000000000000000000000000004e5";
    private static final EntityId entityId = new EntityId(0L, 0L, 3L, EntityType.TOKEN);
    private static final Address INVALID_ADDRESS = Address.fromHexString(INVALID_HEX_ADDRESS);

    @Mock
    private MirrorEntityAccess mirrorEntityAccess;

    @Mock
    private Entity entity;

    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @BeforeEach
    void setup() {
        mirrorEvmContractAliases = new MirrorEvmContractAliases(mirrorEntityAccess);
    }

    @Test
    void resolveForEvmWhenAliasIsPresentShouldReturnMatchingAddress() {
        Address alias = ADDRESS;
        Address address = ADDRESS2;
        mirrorEvmContractAliases.aliases.put(alias, address);

        assertThat(mirrorEvmContractAliases.resolveForEvm(alias)).isEqualTo(address);
    }

    @Test
    void resolveForEvmForContractWhenAliasesNotPresentShouldReturnEntityEvmAddress() {
        when(mirrorEntityAccess.findEntity(ADDRESS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.CONTRACT);
        when(entity.getEvmAddress()).thenReturn(ADDRESS2.toArray());

        final var result = mirrorEvmContractAliases.resolveForEvm(ADDRESS);
        assertThat(result).isEqualTo(ADDRESS2);
    }

    @Test
    void resolveForEvmForTokenWhenNoAliasesShouldReturnEvmAddressFromEntityId() {
        when(mirrorEntityAccess.findEntity(ADDRESS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOKEN);
        when(entity.toEntityId()).thenReturn(entityId);

        final var expected = Bytes.wrap(toEvmAddress(entityId));
        final var result = mirrorEvmContractAliases.resolveForEvm(ADDRESS);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void resolveForEvmForContractWhenNoAliasesShouldReturnEvmAddressFromEntityId() {
        when(mirrorEntityAccess.findEntity(ADDRESS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.CONTRACT);
        when(entity.toEntityId()).thenReturn(entityId);

        final var expected = Bytes.wrap(toEvmAddress(entityId));
        final var result = mirrorEvmContractAliases.resolveForEvm(ADDRESS);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void resolveForEvmForContractShouldReturnFromPendingChangesRightAfterLink() {
        Address alias = ADDRESS;
        Address address = ADDRESS2;
        mirrorEvmContractAliases.link(alias, address);

        final var result = mirrorEvmContractAliases.resolveForEvm(ADDRESS);
        assertThat(result).isEqualTo(address);
    }

    @Test
    void resolveForEvmWhenTypeIsNotTokenOrContractShouldFail() {
        when(mirrorEntityAccess.findEntity(ADDRESS)).thenReturn(Optional.of(entity));
        when(entity.getType()).thenReturn(EntityType.TOPIC);
        assertThatThrownBy(() -> mirrorEvmContractAliases.resolveForEvm(ADDRESS))
                .isInstanceOf(InvalidParametersException.class)
                .hasMessage("Not a contract or token: " + HEX);
    }

    @Test
    void resolveForEvmWhenInvalidAddressShouldFail() {
        assertThatThrownBy(() -> mirrorEvmContractAliases.resolveForEvm(INVALID_ADDRESS))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("No such contract or token: " + HEX2);
    }

    @Test
    void initializeWithEmptyAliasesMap() {
        assertThat(mirrorEvmContractAliases.aliases).isNotNull().isEmpty();
    }

    @Test
    void link() {
        mirrorEvmContractAliases.pendingChanges.clear();

        Address alias = ADDRESS;
        Address address = ADDRESS2;
        mirrorEvmContractAliases.link(alias, address);

        assertThat(mirrorEvmContractAliases.pendingChanges).hasSize(1).hasEntrySatisfying(alias, v -> assertThat(v)
                .isEqualTo(address));
    }

    @Test
    void unlink() {
        mirrorEvmContractAliases.pendingChanges.clear();
        mirrorEvmContractAliases.pendingChanges.put(ADDRESS, ADDRESS2);
        mirrorEvmContractAliases.unlink(ADDRESS);
        assertThat(mirrorEvmContractAliases.aliases).isEmpty();
    }

    @Test
    void publicKeyCouldNotBeParsed() throws InvalidProtocolBufferException, DecoderException {
        final byte[] ECDSA_PUBLIC_KEY =
                Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
        Address recoveredAddress = Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
        Key key = Key.parseFrom(ECDSA_PUBLIC_KEY);
        JKey jKey = JKey.mapKey(key);
        mirrorEvmContractAliases.maybeLinkEvmAddress(jKey, ADDRESS2);

        try (MockedStatic<EthSigsUtils> utilities = Mockito.mockStatic(EthSigsUtils.class)) {
            utilities
                    .when(() -> EthSigsUtils.recoverAddressFromPubKey((byte[]) any()))
                    .thenReturn(new byte[0]);
            assertEquals(Map.of(recoveredAddress, ADDRESS2), mirrorEvmContractAliases.getAliases());
        }
    }

    @Test
    void ignoresNullKeys() {
        assertFalse(
                mirrorEvmContractAliases.maybeLinkEvmAddress(null, ADDRESS2, EthSigsUtils::recoverAddressFromPubKey));
    }
}
