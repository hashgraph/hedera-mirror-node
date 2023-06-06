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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.accessor.AccountDatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.DatabaseAccessor;
import com.hedera.mirror.web3.evm.store.accessor.EntityDatabaseAccessor;
import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmStackedWorldStateUpdaterTest {
    private static final Address alias = Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbb");
    private static final Address alias2 = Address.fromHexString("0xabcdefabcdefabcdefbabcdefabcdefabcdefbbc");
    private static final Address sponsor = Address.fromHexString("0xcba");
    private static final long aBalance = 1_000L;
    private static final long aNonce = 1L;
    private final Address address = Address.fromHexString("0x000000000000000000000000000000000000077e");
    private final UpdateTrackingAccount<Account> updatedHederaEvmAccount = new UpdateTrackingAccount<>(address, null);

    @Mock
    private AccountAccessor accountAccessor;

    @Mock
    private TokenAccessor tokenAccessor;

    @Mock
    private HederaEvmEntityAccess entityAccess;

    @Mock
    private AbstractLedgerEvmWorldUpdater<HederaEvmMutableWorldState, Account> updater;

    @Mock
    private EvmProperties properties;

    @Mock
    private EntityDatabaseAccessor entityDatabaseAccessor;

    private StackedStateFrames<Object> stackedStateFrames;

    @Mock
    private MirrorEvmContractAliases mirrorEvmContractAliases;

    private HederaEvmStackedWorldStateUpdater subject;

    @BeforeEach
    void setUp() {
        final List<DatabaseAccessor<Object, ?>> accessors =
                List.of(new AccountDatabaseAccessor(entityDatabaseAccessor, null, null, null, null, null));
        stackedStateFrames = new StackedStateFrames<>(accessors);
        subject = new HederaEvmStackedWorldStateUpdater(
                updater,
                accountAccessor,
                entityAccess,
                tokenAccessor,
                properties,
                mirrorEvmContractAliases,
                stackedStateFrames);
    }

    @Test
    void commitsNewlyCreatedAccountToStackedStateFrames() {
        assertThat(stackedStateFrames.height()).isEqualTo(1);
        subject.createAccount(address, aNonce, Wei.of(aBalance));
        subject.commit();
        final var topFrame = stackedStateFrames.top();
        final var accountAccessor = topFrame.getAccessor(com.hedera.services.store.models.Account.class);
        final var accountFromTopFrame = accountAccessor.get(address);
        assertTrue(accountFromTopFrame.isPresent());
        assertThat(accountFromTopFrame.get().getAccountAddress()).isEqualTo(address);
        assertThat(stackedStateFrames.height()).isEqualTo(1);
    }

    @Test
    void commitsNewlyCreatedAccountAsExpected() {
        updater = new MockLedgerWorldUpdater(null, accountAccessor);
        subject = new HederaEvmStackedWorldStateUpdater(
                updater,
                accountAccessor,
                entityAccess,
                tokenAccessor,
                properties,
                mirrorEvmContractAliases,
                stackedStateFrames);
        subject.createAccount(address, aNonce, Wei.of(aBalance));
        assertNull(updater.getAccount(address));
        subject.commit();
        assertThat(subject.getAccount(address).getNonce()).isEqualTo(aNonce);
        assertThat(updater.getAccount(address).getNonce()).isEqualTo(aNonce);
    }

    @Test
    void accountTests() {
        updatedHederaEvmAccount.setBalance(Wei.of(100));
        assertThat(subject.createAccount(address, 1, Wei.ONE).getAddress()).isEqualTo(address);
        assertThat(subject.getAccount(address).getBalance()).isEqualTo(Wei.ONE);
        assertThat(subject.getTouchedAccounts()).isNotEmpty();
        assertThat(subject.getDeletedAccountAddresses()).isEmpty();
        subject.commit();
        subject.revert();
        subject.deleteAccount(address);
    }

    @Test
    void get() {
        when(updater.get(address)).thenReturn(updatedHederaEvmAccount);
        when(accountAccessor.canonicalAddress(address)).thenReturn(address);

        final var actual = subject.get(address);
        assertThat(actual.getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getForRedirect() {
        givenForRedirect();
        assertThat(subject.get(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getWithTrack() {
        when(updater.getForMutation(address)).thenReturn(updatedHederaEvmAccount);
        when(accountAccessor.canonicalAddress(address)).thenReturn(address);

        subject.getAccount(address);
        subject.get(address);
        assertThat(subject.get(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getWithNonCanonicalAddress() {
        when(accountAccessor.canonicalAddress(any())).thenReturn(Address.ZERO);
        assertThat(subject.get(address)).isNull();
    }

    @Test
    void getAccount() {
        when(updater.getForMutation(address)).thenReturn(updatedHederaEvmAccount);
        assertThat(subject.getAccount(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getAccountWithTrack() {
        when(updater.getForMutation(address)).thenReturn(updatedHederaEvmAccount);
        subject.getAccount(address);
        assertThat(subject.getAccount(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void getAccountWithMissingWorldReturnsNull() {
        assertThat(subject.getAccount(address)).isNull();
    }

    @Test
    void getAccountForRedirect() {
        givenForRedirect();
        assertThat(subject.getAccount(address).getAddress()).isEqualTo(updatedHederaEvmAccount.getAddress());
    }

    @Test
    void updaterTest() {
        assertThat(subject.tokenAccessor()).isEqualTo(tokenAccessor);
        assertThat(subject.parentUpdater()).isEmpty();
        assertThat(subject.updater()).isEqualTo(subject);
    }

    @Test
    void namedelegatesTokenAccountTest() {
        final var someAddress = Address.BLS12_MAP_FP2_TO_G2;
        assertThat(subject.isTokenAddress(someAddress)).isFalse();
    }

    @Test
    void usesAliasesForDecodingHelp() {
        given(mirrorEvmContractAliases.resolveForEvm(alias)).willReturn(sponsor);
        given(tokenAccessor.canonicalAddress(alias)).willReturn(alias);

        final var resolved = subject.unaliased(alias.toArrayUnsafe());
        assertArrayEquals(sponsor.toArrayUnsafe(), resolved);
    }

    @Test
    void unaliasingFailsWhenNotUsingCanonicalAddress() {
        given(tokenAccessor.canonicalAddress(alias)).willReturn(alias2);

        assertArrayEquals(new byte[20], subject.unaliased(alias.toArrayUnsafe()));
    }

    private void givenForRedirect() {
        when(properties.isRedirectTokenCallsEnabled()).thenReturn(true);
        when(entityAccess.isTokenAccount(address)).thenReturn(true);
    }
}
