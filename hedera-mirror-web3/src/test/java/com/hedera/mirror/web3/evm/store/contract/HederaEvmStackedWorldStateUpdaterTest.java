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

package com.hedera.mirror.web3.evm.store.contract; /*
                                                    * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmMutableWorldState;
import com.hedera.node.app.service.evm.store.models.UpdateTrackingAccount;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import java.util.Collections;
import java.util.Optional;
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
    private final Address address = Address.fromHexString("0x000000000000000000000000000000000000077e");

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

    private HederaEvmStackedWorldStateUpdater subject;
    private final UpdateTrackingAccount<Account> updatedHederaEvmAccount = new UpdateTrackingAccount<>(address, null);

    @BeforeEach
    void setUp() {
        subject = new HederaEvmStackedWorldStateUpdater(
                updater, accountAccessor, entityAccess, tokenAccessor, properties);
    }

    @Test
    void accountTests() {
        given(updater.getForMutation(address)).willReturn(updatedHederaEvmAccount);
        updatedHederaEvmAccount.setBalance(Wei.of(100));
        assertNull(subject.createAccount(address, 1, Wei.ONE));
        assertEquals(Wei.of(100L), subject.getAccount(address).getBalance());
        assertFalse(subject.getTouchedAccounts().isEmpty());
        assertEquals(Collections.emptyList(), subject.getDeletedAccountAddresses());
        subject.commit();
        subject.revert();
        subject.deleteAccount(address);
    }

    @Test
    void get() {
        given(updater.get(address)).willReturn(updatedHederaEvmAccount);
        given(accountAccessor.canonicalAddress(address)).willReturn(address);

        final var actual = subject.get(address);
        assertEquals(updatedHederaEvmAccount.getAddress(), actual.getAddress());
    }

    @Test
    void getForRedirect() {
        givenForRedirect();
        assertEquals(updatedHederaEvmAccount.getAddress(), subject.get(address).getAddress());
    }

    @Test
    void getWithTrack() {
        given(updater.getForMutation(address)).willReturn(updatedHederaEvmAccount);
        given(accountAccessor.canonicalAddress(address)).willReturn(address);

        subject.getAccount(address);
        subject.get(address);
        assertEquals(updatedHederaEvmAccount.getAddress(), subject.get(address).getAddress());
    }

    @Test
    void getWithNonCanonicalAddress() {
        when(accountAccessor.canonicalAddress(any())).thenReturn(Address.ZERO);
        assertNull(subject.get(address));
    }

    @Test
    void getAccount() {
        given(updater.getForMutation(address)).willReturn(updatedHederaEvmAccount);
        assertEquals(
                updatedHederaEvmAccount.getAddress(),
                subject.getAccount(address).getAddress());
    }

    @Test
    void getAccountWithTrack() {
        given(updater.getForMutation(address)).willReturn(updatedHederaEvmAccount);
        subject.getAccount(address);
        assertEquals(
                updatedHederaEvmAccount.getAddress(),
                subject.getAccount(address).getAddress());
    }

    @Test
    void getAccountWithMissingWorldReturnsNull() {
        assertNull(subject.getAccount(address));
    }

    @Test
    void getAccountForRedirect() {
        givenForRedirect();
        assertEquals(
                updatedHederaEvmAccount.getAddress(),
                subject.getAccount(address).getAddress());
    }

    @Test
    void updaterTest() {
        assertEquals(tokenAccessor, subject.tokenAccessor());
        assertEquals(Optional.empty(), subject.parentUpdater());
        assertEquals(subject, subject.updater());
    }

    @Test
    void namedelegatesTokenAccountTest() {
        final var someAddress = Address.BLS12_MAP_FP2_TO_G2;
        assertFalse(subject.isTokenAddress(someAddress));
    }

    private void givenForRedirect() {
        given(properties.isRedirectTokenCallsEnabled()).willReturn(true);
        given(entityAccess.isTokenAccount(address)).willReturn(true);
    }
}
