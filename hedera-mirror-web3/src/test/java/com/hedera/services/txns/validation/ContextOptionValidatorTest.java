/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.validation;

import static com.hedera.services.utils.EntityIdUtils.toGrpcAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.StoreImpl;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContextOptionValidatorTest {
    private final Id id = new Id(0, 0, 13);
    private final AccountID accountId = toGrpcAccountId(id);
    private final Address address = id.asEvmAddress();
    MirrorNodeEvmProperties mirrorNodeEvmProperties;
    Account account;
    private StoreImpl store;
    private ContextOptionValidator subject;

    @BeforeEach
    void setup() {
        mirrorNodeEvmProperties = mock(MirrorNodeEvmProperties.class);
        store = mock(StoreImpl.class);
        subject = new ContextOptionValidator(mirrorNodeEvmProperties);
    }

    @Test
    void shortCircuitsLedgerExpiryCheckIfNoExpiryEnabled() {
        account = new Account(id, 0);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.isAtLeastOneAutoRenewTargetType()).willReturn(false);

        assertEquals(OK, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void shortCircuitsLedgerExpiryCheckIfBalanceIsNonZero() {
        account = new Account(id, 1);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.isAtLeastOneAutoRenewTargetType()).willReturn(true);

        assertEquals(OK, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void shortCircuitsIfBalanceIsZeroButNotDetached() {
        account = new Account(id, 0).setExpiry(System.currentTimeMillis() / 1000 + 1000);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.isAtLeastOneAutoRenewTargetType()).willReturn(true);

        assertEquals(OK, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void shortCircuitsIfContractExpiryNotEnabled() {
        account = new Account(id, 0)
                .setExpiry(System.currentTimeMillis() / 1000 - 1000)
                .setIsSmartContract(true);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.isAtLeastOneAutoRenewTargetType()).willReturn(true);
        assertEquals(OK, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void usesPreciseExpiryCheckIfBalanceIsZero() {
        account = new Account(id, 0)
                .setExpiry(System.currentTimeMillis() / 1000 - 1000)
                .setIsSmartContract(true);
        given(store.getAccount(address, OnMissing.THROW)).willReturn(account);
        given(mirrorNodeEvmProperties.isAtLeastOneAutoRenewTargetType()).willReturn(true);
        given(mirrorNodeEvmProperties.isExpireContracts()).willReturn(true);
        assertEquals(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, subject.expiryStatusGiven(store, accountId));
    }

    @Test
    void alwaysOkExpiryStatusIfNonzeroBalance() {
        final var status = subject.expiryStatusGiven(1L, true, true);
        assertEquals(OK, status);
    }

    @Test
    void alwaysOkIfNotDetached() {
        final var status = subject.expiryStatusGiven(0L, false, true);
        assertEquals(OK, status);
    }

    @Test
    void contractIsExpiredIfZeroBalanceAndPastExpiry() {
        given(mirrorNodeEvmProperties.isExpireContracts()).willReturn(true);
        final var status = subject.expiryStatusGiven(0, true, true);
        assertEquals(CONTRACT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void accountIsExpiredIfZeroBalanceAndDetached() {
        given(mirrorNodeEvmProperties.isExpireAccounts()).willReturn(true);
        final var status = subject.expiryStatusGiven(0, true, false);
        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, status);
    }

    @Test
    void ifAccountExpiryNotEnabledItsOk() {
        final var status = subject.expiryStatusGiven(0, true, false);
        assertEquals(OK, status);
    }

    @Test
    void ifContractExpiryNotEnabledItsOk() {
        final var status = subject.expiryStatusGiven(0, true, true);
        assertEquals(OK, status);
    }
}
