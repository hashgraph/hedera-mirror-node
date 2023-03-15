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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ContractID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
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
        doReturn(transactionHandler).when(transactionHandlerFactory).get(any(TransactionType.class));
        contractResultService = new ContractResultServiceImpl(entityProperties, entityIdService, entityListener,
                sidecarContractMigration, transactionHandlerFactory);
    }

    @Test
    void invalidContractLogId() {
        RecordItem recordItem = recordItemBuilder.contractCreate().build();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.entityId(EntityId.EMPTY).type(recordItem.getTransactionType()))
                .get();

        when(entityIdService.lookup((ContractID) any())).thenReturn(EntityId.EMPTY);

        contractResultService.process(recordItem, transaction);

        verify(entityListener, never()).onContractLog(any());
    }
}
