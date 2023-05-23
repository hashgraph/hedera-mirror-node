/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.domain;

import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.migration.SidecarContractMigration;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandler;
import com.hedera.mirror.importer.parser.record.transactionhandler.TransactionHandlerFactory;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ContractResultServiceImplTest {
    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
    private final EntityProperties entityProperties = new EntityProperties();
    private final DomainBuilder domainBuilder = new DomainBuilder();

    @Mock(strictness = LENIENT)
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
        doReturn(transactionHandler).when(transactionHandlerFactory).get(any(TransactionType.class));
        contractResultService = new ContractResultServiceImpl(
                entityProperties, entityIdService, entityListener, sidecarContractMigration, transactionHandlerFactory);
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    @SneakyThrows
    void verifiesEntityLookup(
            Function<RecordItemBuilder, RecordItem> recordBuilder,
            EntityId entityId,
            boolean recoverableError,
            CapturedOutput capturedOutput) {
        var recordItem = recordBuilder.apply(recordItemBuilder);
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.entityId(entityId).type(recordItem.getTransactionType()))
                .get();

        when(entityIdService.lookup((ContractID) any())).thenReturn(Optional.ofNullable(entityId));

        contractResultService.process(recordItem, transaction);

        verify(entityListener, times(1)).onContractResult(any());

        if (recoverableError) {
            assertThat(capturedOutput.getAll()).containsIgnoringCase(RECOVERABLE_ERROR);
        } else {
            assertThat(capturedOutput.getAll()).doesNotContainIgnoringCase(RECOVERABLE_ERROR);
        }
    }

    private static Stream<Arguments> provideEntities() {
        Function<RecordItemBuilder, RecordItem> withDefaultContractId =
                (RecordItemBuilder builder) -> builder.tokenMint(TokenType.FUNGIBLE_COMMON)
                        .record(x -> x.setContractCallResult(
                                builder.contractFunctionResult(ContractID.getDefaultInstance())))
                        .build();
        Function<RecordItemBuilder, RecordItem> withoutDefaultContractId =
                (RecordItemBuilder builder) -> builder.tokenMint(TokenType.FUNGIBLE_COMMON)
                        .record(x -> x.setContractCallResult(builder.contractFunctionResult()))
                        .build();

        Function<RecordItemBuilder, RecordItem> contractCreate =
                (RecordItemBuilder builder) -> builder.contractCreate().build();

        return Stream.of(
                Arguments.of(withoutDefaultContractId, null, true),
                Arguments.of(withoutDefaultContractId, EntityId.EMPTY, true),
                Arguments.of(withDefaultContractId, null, false),
                Arguments.of(withDefaultContractId, EntityId.EMPTY, false),
                Arguments.of(contractCreate, EntityId.EMPTY, false),
                Arguments.of(contractCreate, null, false),
                Arguments.of(contractCreate, EntityId.of(0, 0, 5, EntityType.CONTRACT), false));
    }
}
