/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state.keyvalue;

import static com.hedera.services.utils.EntityIdUtils.toAccountId;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.state.CommonEntityAccessor;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AliasesReadableKVStateTest {

    @InjectMocks
    private AliasesReadableKVState aliasesReadableKVState;

    @Spy
    private ContractCallContext contractCallContext;

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    private static final ProtoBytes EVM_ADDRESS_BYTES =
            new ProtoBytes(Bytes.wrap("67d8d32e9bf1a9968a5ff53b87d777aa8ebbee69".getBytes()));

    private static final ProtoBytes ALIAS_BYTES = new ProtoBytes(
            Bytes.wrap("3a2102b3c641418e89452cd5202adfd4758f459acb8e364f741fd16cd2db79835d39d2".getBytes()));

    private static final Entity ENTITY_WITH_ALIAS = Entity.builder()
            .shard(1L)
            .realm(2L)
            .num(3L)
            .alias(ALIAS_BYTES.value().toByteArray())
            .build();

    private static final Entity ENTITY_WITH_EVM_ADDRESS = Entity.builder()
            .shard(1L)
            .realm(2L)
            .num(3L)
            .alias(EVM_ADDRESS_BYTES.value().toByteArray())
            .build();

    private static final AccountID ACCOUNT_ID =
            toAccountId(ENTITY_WITH_ALIAS.getShard(), ENTITY_WITH_ALIAS.getRealm(), ENTITY_WITH_ALIAS.getNum());

    @BeforeAll
    static void initStaticMocks() {
        contextMockedStatic = mockStatic(ContractCallContext.class);
    }

    @AfterAll
    static void closeStaticMocks() {
        contextMockedStatic.close();
    }

    @BeforeEach
    void setup() {
        contextMockedStatic.when(ContractCallContext::get).thenReturn(contractCallContext);
    }

    @Test
    void accountNotFoundReturnsNullWithAlias() {
        when(commonEntityAccessor.get(any(Bytes.class), any())).thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void accountNotFoundReturnsNullWithEvmAddress() {
        when(commonEntityAccessor.get(any(Bytes.class), any())).thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(EVM_ADDRESS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void whenTimestampIsNullReturnLatestAccountIDWithAlias() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(ALIAS_BYTES.value(), Optional.empty()))
                .thenReturn(Optional.of(ENTITY_WITH_ALIAS));
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsNullReturnLatestAccountIDWithEvmAddress() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.get(EVM_ADDRESS_BYTES.value(), Optional.empty()))
                .thenReturn(Optional.of(ENTITY_WITH_EVM_ADDRESS));
        assertThat(aliasesReadableKVState.get(EVM_ADDRESS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsHistoricalReturnCorrectAccountIDWithAlias() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.get(ALIAS_BYTES.value(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.of(ENTITY_WITH_ALIAS));
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsHistoricalReturnCorrectAccountIDWithEvmAddress() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.get(EVM_ADDRESS_BYTES.value(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.of(ENTITY_WITH_EVM_ADDRESS));
        assertThat(aliasesReadableKVState.get(EVM_ADDRESS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsLaterReturnNullWithAlias() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.get(ALIAS_BYTES.value(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void whenTimestampIsLaterReturnNullWithEvmAddress() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.get(EVM_ADDRESS_BYTES.value(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(EVM_ADDRESS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void getExpectedSize() {
        assertThat(aliasesReadableKVState.size()).isZero();
    }

    @Test
    void iterateFromDataSourceReturnsEmptyIterator() {
        assertThat(aliasesReadableKVState.iterateFromDataSource()).isEqualTo(Collections.emptyIterator());
    }
}
