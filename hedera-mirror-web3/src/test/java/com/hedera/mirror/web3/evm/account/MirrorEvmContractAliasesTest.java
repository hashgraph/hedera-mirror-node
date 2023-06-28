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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.Key;
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

    private static final String ALIAS_HEX = "0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb";
    private static final Address ALIAS = Address.fromHexString(ALIAS_HEX);

    private static final EntityId entityId = new EntityId(0L, 0L, 3L, EntityType.TOKEN);
    private static final Id id = new Id(0L, 0L, 3L);

    @Mock
    private Store store;

    @Mock
    private Token token;

    @Mock
    private Account account;

    private MirrorEvmContractAliases mirrorEvmContractAliases;

    @BeforeEach
    void setup() {
        mirrorEvmContractAliases = new MirrorEvmContractAliases(store);
    }

    @Test
    void resolveForEvmShouldReturnInputWhenItIsMirrorAddress() {
        assertThat(mirrorEvmContractAliases.resolveForEvm(ADDRESS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmWhenAliasIsPresentShouldReturnMatchingAddressFromAliases() {
        mirrorEvmContractAliases.aliases.put(ALIAS, ADDRESS);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmWhenAliasIsPresentShouldReturnMatchingAddressFromPendingAliases() {
        mirrorEvmContractAliases.pendingAliases.put(ALIAS, ADDRESS);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmWhenAliasAndPendingAliasIsPresentShouldReturnMatchingAddressFromPendingAliases() {
        mirrorEvmContractAliases.pendingAliases.put(ALIAS, ADDRESS);
        mirrorEvmContractAliases.aliases.put(ALIAS, Address.ZERO);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmWhenAliasIsPresentAndIsPendingRemovalShouldReturnEntityEvmAddress() {
        mirrorEvmContractAliases.aliases.put(ALIAS, ADDRESS);
        mirrorEvmContractAliases.pendingRemovals.add(ALIAS);

        when(store.getToken(ALIAS, OnMissing.DONT_THROW)).thenReturn(token);
        when(token.getId()).thenReturn(id);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(Bytes.wrap(toEvmAddress(entityId)));
    }

    @Test
    void resolveForEvmForAccountWhenAliasesNotPresentShouldReturnEntityEvmAddress() {
        when(store.getToken(ALIAS, OnMissing.DONT_THROW)).thenReturn(Token.getEmptyToken());
        when(store.getAccount(ALIAS, OnMissing.THROW)).thenReturn(account);
        when(account.getAccountAddress()).thenReturn(Address.wrap(Bytes.wrap(toEvmAddress(entityId))));

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(Bytes.wrap(toEvmAddress(entityId)));
    }

    @Test
    void resolveForEvmForTokenWhenAliasesNotPresentShouldReturnEntityEvmAddress() {
        when(store.getToken(ALIAS, OnMissing.DONT_THROW)).thenReturn(token);
        when(token.getId()).thenReturn(id);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(Bytes.wrap(toEvmAddress(entityId)));
    }

    @Test
    void resolveForEvmWhenInvalidAddressShouldFail() {
        when(store.getToken(ALIAS, OnMissing.DONT_THROW)).thenReturn(Token.getEmptyToken());
        when(store.getAccount(ALIAS, OnMissing.THROW))
                .thenThrow(new InvalidTransactionException(FAIL_INVALID, "Entity is missing", ""));

        assertThatThrownBy(() -> mirrorEvmContractAliases.resolveForEvm(ALIAS))
                .isInstanceOf(InvalidTransactionException.class);
    }

    @Test
    void initializeWithEmptyAliasesMap() {
        assertThat(mirrorEvmContractAliases.aliases).isNotNull().isEmpty();
        assertThat(mirrorEvmContractAliases.pendingAliases).isNotNull().isEmpty();
        assertThat(mirrorEvmContractAliases.pendingRemovals).isNotNull().isEmpty();
    }

    @Test
    void link() {
        mirrorEvmContractAliases.link(ALIAS, ADDRESS);

        assertThat(mirrorEvmContractAliases.pendingAliases).hasSize(1).containsEntry(ALIAS, ADDRESS);
        assertThat(mirrorEvmContractAliases.pendingRemovals).isEmpty();
    }

    @Test
    void unlink() {
        mirrorEvmContractAliases.pendingAliases.put(ALIAS, ADDRESS);

        mirrorEvmContractAliases.unlink(ALIAS);

        assertThat(mirrorEvmContractAliases.pendingAliases).isEmpty();
        assertThat(mirrorEvmContractAliases.pendingRemovals).hasSize(1).contains(ALIAS);
    }

    @Test
    void commitAddsAllFromPendingAliases() {
        mirrorEvmContractAliases.pendingAliases.put(ALIAS, ADDRESS);

        mirrorEvmContractAliases.commit();

        assertThat(mirrorEvmContractAliases.aliases).containsEntry(ALIAS, ADDRESS);
        assertThat(mirrorEvmContractAliases.pendingAliases).isEmpty();
    }

    @Test
    void commitRemovesAllFromPendingRemovals() {
        mirrorEvmContractAliases.aliases.put(ALIAS, ADDRESS);
        mirrorEvmContractAliases.pendingRemovals.add(ALIAS);

        mirrorEvmContractAliases.commit();

        assertThat(mirrorEvmContractAliases.aliases).doesNotContainEntry(ALIAS, ADDRESS);
        assertThat(mirrorEvmContractAliases.pendingRemovals).isEmpty();
    }

    @Test
    void resetPendingChangesClearsPendingAliases() {
        mirrorEvmContractAliases.pendingAliases.put(ALIAS, ADDRESS);

        mirrorEvmContractAliases.resetPendingChanges();

        assertThat(mirrorEvmContractAliases.pendingAliases).isEmpty();
    }

    @Test
    void resetPendingChangesClearsPendingRemovals() {
        mirrorEvmContractAliases.pendingRemovals.add(ALIAS);

        mirrorEvmContractAliases.resetPendingChanges();

        assertThat(mirrorEvmContractAliases.pendingRemovals).isEmpty();
    }

    @Test
    void isInUseShouldBeTrueIfInPendingAliases() {
        mirrorEvmContractAliases.pendingAliases.put(ALIAS, ADDRESS);

        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isTrue();
    }

    @Test
    void isInUseShouldBeTrueIfInAliasesAndNotInPendingRemovals() {
        mirrorEvmContractAliases.aliases.put(ALIAS, ADDRESS);

        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isTrue();
    }

    @Test
    void isInUseShouldBeFalseIfInAliasesAndInPendingRemovals() {
        mirrorEvmContractAliases.aliases.put(ALIAS, ADDRESS);
        mirrorEvmContractAliases.pendingRemovals.add(ALIAS);

        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isFalse();
    }

    @Test
    void isInUseShouldBeFalseIfNotInAliasesOrPending() {
        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isFalse();
    }

    @Test
    void publicKeyCouldNotBeParsed() throws InvalidProtocolBufferException, DecoderException {
        final byte[] ECDSA_PUBLIC_KEY =
                Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
        Address recoveredAddress = Address.fromHexString("0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b");
        Key key = Key.parseFrom(ECDSA_PUBLIC_KEY);
        JKey jKey = JKey.mapKey(key);
        mirrorEvmContractAliases.maybeLinkEvmAddress(jKey, ADDRESS);

        try (MockedStatic<EthSigsUtils> utilities = Mockito.mockStatic(EthSigsUtils.class)) {
            utilities
                    .when(() -> EthSigsUtils.recoverAddressFromPubKey((byte[]) any()))
                    .thenReturn(new byte[0]);
            assertTrue(mirrorEvmContractAliases.isInUse(recoveredAddress));
        }
    }

    @Test
    void ignoresNullKeys() {
        assertFalse(mirrorEvmContractAliases.maybeLinkEvmAddress(null, ADDRESS));
    }
}
