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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.util.DomainUtils;

class ContractCallTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(ContractID.getDefaultInstance(), contractId))
                .thenReturn(EntityId.of(DEFAULT_ENTITY_NUM, CONTRACT));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractCallTransactionHandler(entityIdService);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractCall(ContractCallTransactionBody.newBuilder().setContractID(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Test
    void testGetEntityIdReceipt() {
        var recordItem = recordItemBuilder.contractCall().build();
        ContractID contractIdBody = recordItem.getTransactionBody().getContractCall().getContractID();
        ContractID contractIdReceipt = recordItem.getTransactionRecord().getReceipt().getContractID();
        EntityId expectedEntityId = EntityId.of(contractIdReceipt);

        when(entityIdService.lookup(contractIdReceipt, contractIdBody)).thenReturn(expectedEntityId);
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }

    @Test
    void updateContractResultEmptyContractCallFunctionParams() {
        ContractResult contractResult = new ContractResult();
        var recordItem = recordItemBuilder.contractCall().build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        var transaction = recordItem.getTransactionBody().getContractCall();
        assertThat(contractResult)
                .returns(transaction.getAmount(), ContractResult::getAmount)
                .returns(transaction.getGas(), ContractResult::getGasLimit)
                .returns(DomainUtils.toBytes(transaction.getFunctionParameters()),
                        ContractResult::getFunctionParameters);
    }

    @Test
    void updateContractResultNonContractCallTransaction() {
        ContractResult contractResult = ContractResult.builder().build();
        var recordItem = recordItemBuilder.contractCreate().build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        assertThat(contractResult)
                .returns(null, ContractResult::getAmount)
                .returns(null, ContractResult::getGasLimit)
                .returns(null, ContractResult::getFunctionParameters);
    }
}
