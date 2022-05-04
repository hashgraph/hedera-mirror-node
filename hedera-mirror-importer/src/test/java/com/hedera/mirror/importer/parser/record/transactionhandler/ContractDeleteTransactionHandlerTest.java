package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;

class ContractDeleteTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    private static final long OBTAINER_NUM = 99L;

    private final ContractID obtainerId = ContractID.newBuilder().setContractNum(OBTAINER_NUM).build();

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(ContractID.getDefaultInstance(), contractId))
                .thenReturn(EntityId.of(DEFAULT_ENTITY_NUM, CONTRACT));
        when(entityIdService.lookup(obtainerId)).thenReturn(EntityId.of(OBTAINER_NUM, CONTRACT));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractDeleteTransactionHandler(entityIdService, entityListener, new RecordParserProperties());
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractDeleteInstance(ContractDeleteTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(DEFAULT_ENTITY_NUM).build())
                        .setTransferAccountID(AccountID.newBuilder().setAccountNum(OBTAINER_NUM).build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecs() {
        List<UpdateEntityTestSpec> specs = new ArrayList<>();
        Contract expected = (Contract) getExpectedEntityWithTimestamp();
        expected.setDeleted(true);
        expected.setObtainerId(EntityId.of(OBTAINER_NUM, EntityType.ACCOUNT));
        expected.setPermanentRemoval(false);

        specs.add(
                UpdateEntityTestSpec.builder()
                        .description("Delete with account obtainer")
                        .expected(expected)
                        .recordItem(getRecordItem(getDefaultTransactionBody().build(),
                                getDefaultTransactionRecord().build()))
                        .build()
        );

        TransactionBody.Builder transactionBody = TransactionBody.newBuilder()
                .setContractDeleteInstance(ContractDeleteTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(DEFAULT_ENTITY_NUM).build())
                        .setPermanentRemoval(true)
                        .setTransferContractID(ContractID.newBuilder().setContractNum(OBTAINER_NUM).build()));

        expected = TestUtils.clone(expected);
        expected.setPermanentRemoval(true);
        specs.add(
                UpdateEntityTestSpec.builder()
                        .description("Delete with contract obtainer and permanent removal")
                        .expected(expected)
                        .recordItem(getRecordItem(transactionBody.build(),
                                getDefaultTransactionRecord().build()))
                        .build()
        );
        return specs;
    }

    @Test
    void testGetEntityIdReceipt() {
        var recordItem = recordItemBuilder.contractDelete().build();
        ContractID contractIdBody = recordItem.getTransactionBody().getContractDeleteInstance().getContractID();
        ContractID contractIdReceipt = recordItem.getRecord().getReceipt().getContractID();
        EntityId expectedEntityId = EntityId.of(contractIdReceipt);

        when(entityIdService.lookup(contractIdReceipt, contractIdBody)).thenReturn(expectedEntityId);
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }
}
