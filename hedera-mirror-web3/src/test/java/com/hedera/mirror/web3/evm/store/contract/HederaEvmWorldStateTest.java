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

package com.hedera.mirror.web3.evm.store.contract;

import static com.hedera.services.utils.EntityIdUtils.accountIdFromEvmAddress;
import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ContractID;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmWorldStateTest {
    @Mock
    private HederaEvmEntityAccess hederaEvmEntityAccess;

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private AbstractCodeCache abstractCodeCache;

    private final Address address = Address.fromHexString("0x000000000000000000000000000000000000077e");
    final long balance = 1_234L;

    @Mock
    AccountAccessor accountAccessor;

    @Mock
    TokenAccessor tokenAccessor;

    @Mock
    EntityAddressSequencer entityAddressSequencer;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    private StackedStateFrames<Object> stackedStateFrames;

    @Mock
    MirrorEvmContractAliases mirrorEvmContractAliases;

    private HederaEvmWorldState subject;

    @BeforeEach
    void setUp() {
        final List<DatabaseAccessor<Object, ?>> accessors =
                List.of(new AccountDatabaseAccessor(entityDatabaseAccessor, null, null, null, null, null));
        stackedStateFrames = new StackedStateFrames<>(accessors);
        subject = new HederaEvmWorldState(
                hederaEvmEntityAccess,
                evmProperties,
                abstractCodeCache,
                accountAccessor,
                tokenAccessor,
                entityAddressSequencer,
                mirrorEvmContractAliases,
                stackedStateFrames);
    }

    @Test
    void rootHash() {
        assertThat(subject.rootHash()).isEqualTo(Hash.EMPTY);
    }

    @Test
    void frontierRootHash() {
        assertThat(subject.frontierRootHash()).isEqualTo(Hash.EMPTY);
    }

    @Test
    void streamAccounts() {
        assertThrows(UnsupportedOperationException.class, () -> subject.streamAccounts(null, 10));
    }

    @Test
    void returnsNullForNull() {
        assertThat(subject.get(null)).isNull();
    }

    @Test
    void returnsNull() {
        assertThat(subject.get(address)).isNull();
    }

    @Test
    void returnsWorldStateAccount() {
        final var address = Address.RIPEMD160;
        when(hederaEvmEntityAccess.getBalance(address)).thenReturn(balance);
        when(hederaEvmEntityAccess.isUsable(any())).thenReturn(true);

        final var account = subject.get(address);

        assertThat(account.getCode().isEmpty()).isTrue();
        assertThat(account.hasCode()).isFalse();
    }

    @Test
    void returnsHederaEvmWorldStateTokenAccount() {
        final var address = Address.RIPEMD160;
        when(hederaEvmEntityAccess.isTokenAccount(address)).thenReturn(true);
        when(evmProperties.isRedirectTokenCallsEnabled()).thenReturn(true);

        final var account = subject.get(address);

        assertThat(account.getCode().isEmpty()).isFalse();
        assertThat(account.hasCode()).isTrue();
    }

    @Test
    void returnsNull2() {
        final var address = Address.RIPEMD160;
        when(hederaEvmEntityAccess.isTokenAccount(address)).thenReturn(true);
        when(evmProperties.isRedirectTokenCallsEnabled()).thenReturn(false);

        assertThat(subject.get(address)).isNull();
    }

    @Test
    void commitsNewlyCreatedAccountToStackedStateFrames() {
        final var actualSubject = subject.updater();
        assertThat(stackedStateFrames.height()).isEqualTo(1);
        stackedStateFrames.push();
        stackedStateFrames.push();
        var topFrame = stackedStateFrames.top();
        var accountAccessor = topFrame.getAccessor(com.hedera.services.store.models.Account.class);
        final var accountModel = new com.hedera.services.store.models.Account(
                Id.fromGrpcAccount(accountIdFromEvmAddress(address.toArrayUnsafe())),
                0L,
                123L,
                false,
                0L,
                0L,
                null,
                0,
                Collections.emptySortedMap(),
                Collections.emptySortedMap(),
                Collections.emptySortedSet(),
                0,
                0,
                0,
                0L);
        accountAccessor.set(address, accountModel);
        actualSubject.commit();
        topFrame = stackedStateFrames.top();
        accountAccessor = topFrame.getAccessor(com.hedera.services.store.models.Account.class);
        final var accountFromTopFrame = accountAccessor.get(address);
        assertTrue(accountFromTopFrame.isPresent());
        assertThat(accountFromTopFrame.get()).isEqualTo(accountModel);
        assertThat(stackedStateFrames.height()).isEqualTo(2);
    }

    @Test
    void updater() {
        var actualSubject = subject.updater();
        assertThat(actualSubject.getSbhRefund()).isZero();
        assertThat(actualSubject.updater().get(Address.RIPEMD160)).isNull();
    }

    @Test
    void newContractAddressReturnsSequencerValueAsTypedAddress() {
        var actualSubject = subject.updater();
        final Address sponsor = Address.fromHexString("0x23f5e49569a835d7bf9aefd30e4f60cdd570f225");
        final ContractID contractID = ContractID.newBuilder().build();

        when(entityAddressSequencer.getNewContractId(sponsor)).thenReturn(contractID);

        final var actual = actualSubject.newContractAddress(sponsor);
        assertThat(actual).isEqualTo(asTypedEvmAddress(contractID));
    }
}
