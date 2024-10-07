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

package com.hedera.mirror.web3.state;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.pbj.runtime.OneOf;
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
public class AliasesReadableKVStateTest {

    @InjectMocks
    private AliasesReadableKVState aliasesReadableKVState;

    @Spy
    private ContractCallContext contractCallContext;

    private static MockedStatic<ContractCallContext> contextMockedStatic;

    @Mock
    private CommonEntityAccessor commonEntityAccessor;

    private static final ProtoBytes ALIAS_BYTES = new ProtoBytes(Bytes.wrap(new byte[] {2, 3, 4, 5}));

    private static final AccountID ACCOUNT_ID =
            new AccountID(1L, 0L, new OneOf<>(AccountOneOfType.ALIAS, ALIAS_BYTES.value()));

    private static final Entity ENTITY = Entity.builder()
            .shard(ACCOUNT_ID.shardNum())
            .realm(ACCOUNT_ID.realmNum())
            .alias(ALIAS_BYTES.value().toByteArray())
            .build();

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
    void accountNotFoundReturnsNull() {
        when(commonEntityAccessor.getEntityByAliasAndTimestamp(any(), any())).thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isNull());
    }

    @Test
    void whenTimestampIsNullReturnLatestAccountID() {
        when(contractCallContext.getTimestamp()).thenReturn(Optional.empty());
        when(commonEntityAccessor.getEntityByAliasAndTimestamp(
                        ALIAS_BYTES.value().toByteArray(), Optional.empty()))
                .thenReturn(Optional.of(ENTITY));
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsHistoricalReturnCorrectAccountID() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.getEntityByAliasAndTimestamp(
                        ALIAS_BYTES.value().toByteArray(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.of(ENTITY));
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
                .satisfies(accountID -> assertThat(accountID).isEqualTo(ACCOUNT_ID));
    }

    @Test
    void whenTimestampIsLaterReturnNull() {
        final var blockTimestamp = 1234567L;
        when(contractCallContext.getTimestamp()).thenReturn(Optional.of(blockTimestamp));
        when(commonEntityAccessor.getEntityByAliasAndTimestamp(
                        ALIAS_BYTES.value().toByteArray(), Optional.of(blockTimestamp)))
                .thenReturn(Optional.empty());
        assertThat(aliasesReadableKVState.get(ALIAS_BYTES))
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
