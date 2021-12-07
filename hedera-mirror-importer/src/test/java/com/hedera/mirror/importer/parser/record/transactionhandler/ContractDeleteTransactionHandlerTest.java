package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.ArrayList;
import java.util.List;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;

class ContractDeleteTransactionHandlerTest extends AbstractDeleteOrUndeleteTransactionHandlerTest {

    private static final long OBTAINER_ID = 99L;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractDeleteTransactionHandler(entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractDeleteInstance(ContractDeleteTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(DEFAULT_ENTITY_NUM).build())
                        .setTransferAccountID(AccountID.newBuilder().setAccountNum(OBTAINER_ID).build()));
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
        expected.setObtainerId(EntityId.of(OBTAINER_ID, EntityType.ACCOUNT));

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
                        .setTransferContractID(ContractID.newBuilder().setContractNum(OBTAINER_ID).build()));

        specs.add(
                UpdateEntityTestSpec.builder()
                        .description("Delete with contract obtainer")
                        .expected(expected)
                        .recordItem(getRecordItem(transactionBody.build(),
                                getDefaultTransactionRecord().build()))
                        .build()
        );
        return specs;
    }
}
