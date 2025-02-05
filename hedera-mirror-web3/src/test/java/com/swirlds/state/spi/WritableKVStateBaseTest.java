/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.state.spi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.state.core.MapWritableKVState;
import com.hedera.mirror.web3.state.keyvalue.AccountReadableKVState;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableKVStateBaseTest {

    @Mock
    private ReadableKVStateBase<AccountID, Account> readableKVStateBase;

    @Test
    void testResetCache() {
        ContractCallContext.run(ctx -> {
            final var accountID = mock(AccountID.class);
            final var account = mock(Account.class);
            final Map<Object, Object> map = Map.of(accountID, account);
            final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                    new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
            ctx.getModificationsState(AccountReadableKVState.KEY).put(accountID, account);
            assertThat(ctx.getModificationsState(AccountReadableKVState.KEY)).isEqualTo(map);
            writableKVStateBase.reset();
            assertThat(ctx.getModificationsState(AccountReadableKVState.KEY)).isEqualTo(Map.of());
            return ctx;
        });
    }

    @Test
    void testGetOriginalValue() {
        ContractCallContext.run(ctx -> {
            final var accountID = mock(AccountID.class);
            final var account = mock(Account.class);
            final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                    new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
            when(readableKVStateBase.get(accountID)).thenReturn(account);
            assertThat(writableKVStateBase.getOriginalValue(accountID)).isEqualTo(account);
            return ctx;
        });
    }

    @Test
    void testGetForModifyFromModificationsCache() {
        ContractCallContext.run(ctx -> {
            final var accountID = mock(AccountID.class);
            final var account = mock(Account.class);
            final Map<Object, Object> map = Map.of(accountID, account);
            final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                    new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
            ctx.getModificationsState(AccountReadableKVState.KEY).put(accountID, account);
            assertThat(writableKVStateBase.getForModify(accountID)).isEqualTo(account);
            return ctx;
        });
    }

    @Test
    void testGetForModifyFromReadCache() {
        ContractCallContext.run(ctx -> {
            final var accountID = mock(AccountID.class);
            final var account = mock(Account.class);
            final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                    new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
            ctx.getReadCacheState(AccountReadableKVState.KEY).put(accountID, account);
            assertThat(writableKVStateBase.getForModify(accountID)).isEqualTo(account);
            return ctx;
        });
    }

    @Test
    void testWithRemovedEntry() {
        ContractCallContext.run(ctx -> {
            final var accountID = mock(AccountID.class);
            final var account = mock(Account.class);
            final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                    new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
            ctx.getReadCacheState(AccountReadableKVState.KEY).put(accountID, account);
            ctx.getModificationsState(AccountReadableKVState.KEY).put(accountID, null); // The entry was removed
            assertThat(writableKVStateBase.size()).isZero();
            return ctx;
        });
    }
}
