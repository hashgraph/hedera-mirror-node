/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapWritableKVStateTest {

    private MapWritableKVState<AccountID, Account> mapWritableKVState;

    @Mock
    private Map<AccountID, Account> backingStore;

    @Mock
    private AccountID accountID;

    @Mock
    private Account account;

    @BeforeEach
    void setup() {
        mapWritableKVState = new MapWritableKVState<>("ACCOUNTS", backingStore);
    }

    @Test
    void testGetForModifyFromDataSourceReturnsCorrectValue() {
        when(backingStore.get(accountID)).thenReturn(account);
        assertThat(mapWritableKVState.getForModifyFromDataSource(accountID)).isEqualTo(account);
    }

    @Test
    void testDataSourceSizeIsZero() {
        assertThat(mapWritableKVState.sizeOfDataSource()).isZero();
    }

    @Test
    void testReadFromDataSourceReturnsCorrectValue() {
        when(backingStore.get(accountID)).thenReturn(account);
        assertThat(mapWritableKVState.readFromDataSource(accountID)).isEqualTo(account);
    }

    @Test
    void testIterateFromDataSourceReturnsEmptyIterator() {
        final var backingStore = new HashMap<AccountID, Account>();
        final var mapWritableKVState = new MapWritableKVState<>("ACCOUNTS", backingStore);
        mapWritableKVState.putIntoDataSource(accountID, account);
        assertThat(mapWritableKVState.iterateFromDataSource().next())
                .isEqualTo(backingStore.keySet().iterator().next());
    }

    @Test
    void testPutIntoDataSource() {
        final var map = new HashMap<AccountID, Account>();
        final var kvState = new MapWritableKVState<>("ACCOUNTS", map);
        kvState.putIntoDataSource(accountID, account);
        assertThat(map).containsEntry(accountID, account);
    }

    @Test
    void testRemoveFromDataSource() {
        final var map = new HashMap<AccountID, Account>();
        final var kvState = new MapWritableKVState<>("ACCOUNTS", map);
        map.put(accountID, account);
        assertThat(kvState.contains(accountID)).isTrue();
        kvState.removeFromDataSource(accountID);
        assertThat(map.get(accountID)).isNull();
    }

    @Test
    void testCommit() {
        final var map = new HashMap<AccountID, Account>();
        final var kvState = new MapWritableKVState<>("ACCOUNTS", map);
        kvState.put(accountID, account);
        assertThat(kvState.modifiedKeys()).isNotEmpty();
        kvState.commit();
        assertThat(kvState.modifiedKeys()).isEmpty();
    }

    @Test
    void testEqualsSameInstance() {
        assertThat(mapWritableKVState).isEqualTo(mapWritableKVState);
    }

    @Test
    void testEqualsDifferentType() {
        assertThat(mapWritableKVState).isNotEqualTo("someString");
    }

    @Test
    void testEqualsWithNull() {
        assertThat(mapWritableKVState).isNotEqualTo(null);
    }
}
