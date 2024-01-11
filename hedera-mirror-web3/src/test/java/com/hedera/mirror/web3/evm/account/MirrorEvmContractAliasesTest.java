/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.node.app.service.evm.utils.EthSigsUtils;
import com.hedera.services.jproto.JKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.Key;
import java.security.InvalidKeyException;
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

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class MirrorEvmContractAliasesTest {

    private static final String HEX = "0x00000000000000000000000000000000000004e4";
    private static final Address ADDRESS = Address.fromHexString(HEX);

    private static final String ALIAS_HEX = "0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb";
    private static final Address ALIAS = Address.fromHexString(ALIAS_HEX);

    private static final EntityId entityId = EntityId.of(0L, 0L, 1252L);
    private static final Id id = new Id(0L, 0L, 1252L);

    @Mock
    private Store store;

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
    void resolveForEvmWhenAliasIsPresentShouldReturnMatchingAddress() {
        given(store.getAccount(ALIAS, OnMissing.DONT_THROW)).willReturn(account);
        given(account.getId()).willReturn(id);
        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmWhenAliasAndPendingAliasIsPresentShouldReturnMatchingAddressFromPendingAliases() {
        given(store.getAccount(ALIAS, OnMissing.DONT_THROW)).willReturn(account);
        given(account.getId()).willReturn(id);
        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(ADDRESS);
    }

    @Test
    void resolveForEvmForAccountWhenAliasesNotPresentShouldReturnEntityEvmAddress() {
        when(store.getAccount(ALIAS, OnMissing.DONT_THROW)).thenReturn(account);
        when(account.getId()).thenReturn(id);

        assertThat(mirrorEvmContractAliases.resolveForEvm(ALIAS)).isEqualTo(Bytes.wrap(toEvmAddress(entityId)));
    }

    //    @Test
    //    void link() {
    //        mirrorEvmContractAliases.link(ALIAS, ADDRESS);
    //        ContractCallContext contractCallContext = ContractCallContext.get();
    //        assertThat(contractCallContext.getPendingAliases()).hasSize(1).containsEntry(ALIAS, ADDRESS);
    //        assertThat(contractCallContext.getPendingRemovals()).isEmpty();
    //    }

    //    @Test
    //    void unlink() {
    //        ContractCallContext contractCallContext = ContractCallContext.get();
    //        pendingAliases.put(ALIAS, ADDRESS);
    //
    //        mirrorEvmContractAliases.unlink(ALIAS);
    //
    //        assertThat(pendingAliases).isEmpty();
    //        assertThat(contractCallContext.getPendingRemovals()).hasSize(1).contains(ALIAS);
    //    }

    @Test
    void isInUseShouldBeTrueIfAliasesIsPresent() {
        given(store.exists(ALIAS)).willReturn(true);
        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isTrue();
    }

    @Test
    void isInUseShouldBeFalseIfInAliasesAndInPendingRemovals() {
        given(store.exists(ALIAS)).willReturn(false);
        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isFalse();
    }

    @Test
    void isInUseShouldBeFalseIfNotInAliasesOrPending() {
        assertThat(mirrorEvmContractAliases.isInUse(ALIAS)).isFalse();
    }

    @Test
    void publicKeyCouldNotBeParsed() throws InvalidProtocolBufferException, InvalidKeyException {
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
            given(store.exists(recoveredAddress)).willReturn(true);
            assertTrue(mirrorEvmContractAliases.isInUse(recoveredAddress));
        }
    }

    @Test
    void ignoresNullKeys() {
        assertFalse(mirrorEvmContractAliases.maybeLinkEvmAddress(null, ADDRESS));
    }
}
