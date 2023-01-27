package com.hedera.mirror.importer.domain;

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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.fromBytes;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ContractID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.importer.migration.SidecarContractMigration;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;

@ExtendWith(MockitoExtension.class)
class ContractResultServiceImplTest {
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final EntityProperties entityProperties = new EntityProperties();
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock(lenient = true)
    private EntityIdService entityIdService;
    @Mock
    private EntityListener entityListener;
    @Mock
    private SidecarContractMigration sidecarContractMigration;
    @Mock
    private TransactionHandlerFactory transactionHandlerFactory;
    @Mock
    private TransactionHandler transactionHandler;

    private ContractResultService contractResultService;

    @BeforeEach
    void beforeEach() {
        doNothing().when(transactionHandler).updateContractResult(any(ContractResult.class), any(RecordItem.class));
        doReturn(transactionHandler).when(transactionHandlerFactory).get(any(TransactionType.class));
        contractResultService = new ContractResultServiceImpl(entityProperties, entityIdService, entityListener,
                sidecarContractMigration, transactionHandlerFactory);
    }

    @CsvSource({
            "-1,-1,-1,1000,1000",
            "1,1,1,1000,1000",
            "0,0,9223372036854775807,1000,1000",
            "0,0,-1,0,2",
    })
    @ParameterizedTest
    void create2ContractIdWorkaroundCreate(long shard, long realm, long num, long resolvedNum, long expectedNum) {
        var invalidContractId = ContractID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setContractNum(num)
                .build();
        var evmAddress = ContractID.newBuilder().setEvmAddress(fromBytes(toEvmAddress(invalidContractId))).build();
        var expectedId = EntityId.of(expectedNum, CONTRACT);
        var resolvedId = EntityId.of(resolvedNum, CONTRACT);

        var transaction = domainBuilder.transaction().customize(t -> t.entityId(expectedId)).get();
        var recordItem = recordItemBuilder.contractCreate()
                .record(r -> {
                    r.getContractCreateResultBuilder().getLogInfoBuilder(0).setContractID(invalidContractId);
                    r.getContractCreateResultBuilder().removeLogInfo(1);
                })
                .sidecarRecords(r -> r.remove(0))
                .build();

        if (shard == 0 && realm == 0) {
            when(entityIdService.lookup(invalidContractId))
                    .thenThrow(new RuntimeException(new InvalidEntityException("")));
        }
        when(entityIdService.lookup(evmAddress)).thenReturn(resolvedId);

        contractResultService.process(recordItem, transaction);

        verify(entityListener).onContractLog(assertArg(l -> assertThat(l.getContractId()).isEqualTo(expectedId)));
        verify(entityListener, never()).onContractStateChange(any());
        verify(entityListener).onContractResult(isA(ContractResult.class));
    }

    @CsvSource({
            "-1,-1,-1,1000,1000",
            "1,1,1,1000,1000",
            "0,0,9223372036854775807,1000,1000",
            "0,0,-1,0,2",
    })
    @ParameterizedTest
    void create2ContractIdWorkaroundCall(long shard, long realm, long num, long resolvedNum, long expectedNum) {
        var invalidContractId = ContractID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setContractNum(num)
                .build();
        var evmAddress = ContractID.newBuilder().setEvmAddress(fromBytes(toEvmAddress(invalidContractId))).build();
        var expectedId = EntityId.of(expectedNum, CONTRACT);
        var resolvedId = EntityId.of(resolvedNum, CONTRACT);

        var transaction = domainBuilder.transaction().customize(t -> t.entityId(expectedId)).get();
        var recordItem = recordItemBuilder.contractCall()
                .record(r -> {
                    r.getContractCallResultBuilder().getLogInfoBuilder(0).setContractID(invalidContractId);
                    r.getContractCallResultBuilder().removeLogInfo(1);
                })
                .sidecarRecords(r -> r.remove(0))
                .build();

        if (shard == 0 && realm == 0) {
            when(entityIdService.lookup(invalidContractId))
                    .thenThrow(new RuntimeException(new InvalidEntityException("")));
        }
        when(entityIdService.lookup(evmAddress)).thenReturn(resolvedId);
        contractResultService.process(recordItem, transaction);

        verify(entityListener).onContractLog(assertArg(l -> assertThat(l.getContractId()).isEqualTo(expectedId)));
        verify(entityListener, never()).onContractStateChange(any());
        verify(entityListener).onContractResult(isA(ContractResult.class));
    }

    private <T> T assertArg(Consumer<T> asserter) {
        return argThat(t -> {
            asserter.accept(t);
            return true;
        });
    }
}
