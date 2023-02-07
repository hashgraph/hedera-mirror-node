package com.hedera.mirror.importer.parser.record.transactionhandler;

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
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

class SystemUndeleteTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    SystemUndeleteTransactionHandlerTest() {
        super(false);
    }

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(contractId)).thenReturn(EntityId.of(DEFAULT_ENTITY_NUM, CONTRACT));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new SystemUndeleteTransactionHandler(entityIdService, entityListener, new RecordParserProperties());
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setSystemUndelete(SystemUndeleteTransactionBody.newBuilder()
                        .setFileID(FileID.newBuilder().setFileNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.FILE;
    }

    // SystemUndelete for file is tested by common test case in AbstractTransactionHandlerTest.
    // Test SystemUndelete for contract here.
    @Test
    void testSystemUndeleteForContract() {
        TransactionBody transactionBody = TransactionBody.newBuilder()
                .setSystemUndelete(SystemUndeleteTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(DEFAULT_ENTITY_NUM).build()))
                .build();

        testGetEntityIdHelper(transactionBody, getDefaultTransactionRecord().build(),
                EntityId.of(0L, 0L, DEFAULT_ENTITY_NUM, EntityType.CONTRACT));
    }
}
