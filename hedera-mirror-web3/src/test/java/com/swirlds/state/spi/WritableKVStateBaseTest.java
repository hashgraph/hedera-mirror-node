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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.mirror.web3.ContextExtension;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.state.core.MapWritableKVState;
import com.hedera.mirror.web3.state.keyvalue.AccountReadableKVState;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(ContextExtension.class)
@ExtendWith(MockitoExtension.class)
class WritableKVStateBaseTest {

    @Mock
    private ReadableKVStateBase<AccountID, Account> readableKVStateBase;

    @Test
    void testResetCache() {
        final var ctx = ContractCallContext.get();
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final Map<Object, Object> map = Map.of(accountID, account);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
        ctx.getWriteCacheState(AccountReadableKVState.KEY).put(accountID, account);
        assertThat(ctx.getWriteCacheState(AccountReadableKVState.KEY)).isEqualTo(map);
        writableKVStateBase.reset();
        assertThat(ctx.getWriteCacheState(AccountReadableKVState.KEY)).isEqualTo(Map.of());
    }

    @Test
    void testGetOriginalValue() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
        when(readableKVStateBase.get(accountID)).thenReturn(account);
        assertThat(writableKVStateBase.getOriginalValue(accountID)).isEqualTo(account);
    }

    @Test
    void testGetForModifyFromModificationsCache() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
        ContractCallContext.get().getWriteCacheState(AccountReadableKVState.KEY).put(accountID, account);
        assertThat(writableKVStateBase.getForModify(accountID)).isEqualTo(account);
    }

    @Test
    void testGetForModifyFromReadCache() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
        ContractCallContext.get().getReadCacheState(AccountReadableKVState.KEY).put(accountID, account);
        assertThat(writableKVStateBase.getForModify(accountID)).isEqualTo(account);
    }

    @Test
    void testGetForModifyFromDataSource() {
        final var accountID = mock(AccountID.class);
        final var account = mock(Account.class);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
        when(readableKVStateBase.get(accountID)).thenReturn(account);
        assertThat(writableKVStateBase.getForModify(accountID)).isEqualTo(account);
    }

    @Test
    void testSizeWithRemovedEntry() {
        final var ctx = ContractCallContext.get();
        final var accountID = mock(AccountID.class);
        final var accountID2 = mock(AccountID.class);
        final var account = mock(Account.class);
        final var account2 = mock(Account.class);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
        ctx.getReadCacheState(AccountReadableKVState.KEY).put(accountID, account);
        ctx.getReadCacheState(AccountReadableKVState.KEY).put(accountID2, account2);
        ctx.getWriteCacheState(AccountReadableKVState.KEY).put(accountID, null); // The entry was removed
        when(readableKVStateBase.size()).thenReturn(2L);
        when(readableKVStateBase.get(accountID)).thenReturn(account);
        assertThat(writableKVStateBase.size()).isEqualTo(1L);
    }

    @Test
    void testKeysEmpty() {
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
        when(readableKVStateBase.keys()).thenReturn(Collections.emptyIterator());
        final var iterator = writableKVStateBase.keys();
        assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void testKeysWithRemovedEntry() {
        final var ctx = ContractCallContext.get();
        final var accountID = mock(AccountID.class);
        final var accountID2 = mock(AccountID.class);
        final var account = mock(Account.class);
        final var account2 = mock(Account.class);
        final WritableKVStateBase<AccountID, Account> writableKVStateBase =
                new MapWritableKVState<>(AccountReadableKVState.KEY, readableKVStateBase);
        ctx.getReadCacheState(AccountReadableKVState.KEY).put(accountID, account);
        ctx.getReadCacheState(AccountReadableKVState.KEY).put(accountID2, account2);
        ctx.getWriteCacheState(AccountReadableKVState.KEY).put(accountID, null); // The entry was removed
        when(readableKVStateBase.keys())
                .thenReturn(Map.of(accountID, account, accountID2, account2)
                        .keySet()
                        .iterator());
        assertThat(writableKVStateBase.keys().next()).isEqualTo(accountID2);
    }
}
