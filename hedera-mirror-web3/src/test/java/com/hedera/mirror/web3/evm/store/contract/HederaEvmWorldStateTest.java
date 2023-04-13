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

package com.hedera.mirror.web3.evm.store.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.evm.accounts.AccountAccessor;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractCodeCache;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
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

    private HederaEvmWorldState subject;

    @BeforeEach
    void setUp() {
        subject = new HederaEvmWorldState(
                hederaEvmEntityAccess,
                evmProperties,
                abstractCodeCache,
                accountAccessor,
                tokenAccessor,
                entityAddressSequencer);
    }

    @Test
    void rootHash() {
        assertEquals(Hash.EMPTY, subject.rootHash());
    }

    @Test
    void frontierRootHash() {
        assertEquals(Hash.EMPTY, subject.frontierRootHash());
    }

    @Test
    void streamAccounts() {
        assertThrows(UnsupportedOperationException.class, () -> subject.streamAccounts(null, 10));
    }

    @Test
    void returnsNullForNull() {
        assertNull(subject.get(null));
    }

    @Test
    void returnsNull() {
        assertNull(subject.get(address));
    }

    @Test
    void returnsWorldStateAccount() {
        final var address = Address.RIPEMD160;
        given(hederaEvmEntityAccess.getBalance(address)).willReturn(balance);
        given(hederaEvmEntityAccess.isUsable(any())).willReturn(true);

        final var account = subject.get(address);

        assertTrue(account.getCode().isEmpty());
        assertFalse(account.hasCode());
    }

    @Test
    void returnsHederaEvmWorldStateTokenAccount() {
        final var address = Address.RIPEMD160;
        given(hederaEvmEntityAccess.isTokenAccount(address)).willReturn(true);
        given(evmProperties.isRedirectTokenCallsEnabled()).willReturn(true);

        final var account = subject.get(address);

        assertFalse(account.getCode().isEmpty());
        assertTrue(account.hasCode());
    }

    @Test
    void returnsNull2() {
        final var address = Address.RIPEMD160;
        given(hederaEvmEntityAccess.isTokenAccount(address)).willReturn(true);
        given(evmProperties.isRedirectTokenCallsEnabled()).willReturn(false);

        assertNull(subject.get(address));
    }

    @Test
    void updater() {
        var actualSubject = subject.updater();
        assertEquals(0, actualSubject.getSbhRefund());
        assertNull(actualSubject.updater().get(Address.RIPEMD160));
    }
}
