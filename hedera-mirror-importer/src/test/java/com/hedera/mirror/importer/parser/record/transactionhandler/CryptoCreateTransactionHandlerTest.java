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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.mirror.importer.util.UtilityTest;

class CryptoCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @Captor
    private ArgumentCaptor<Entity> entities;

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoCreateTransactionHandler(entityIdService, entityListener, new RecordParserProperties());
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum)
                .setAccountID(AccountID.newBuilder().setAccountNum(DEFAULT_ENTITY_NUM).build());
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.ACCOUNT;
    }

    @Override
    protected AbstractEntity getExpectedUpdatedEntity() {
        AbstractEntity entity = super.getExpectedUpdatedEntity();
        entity.setMaxAutomaticTokenAssociations(0);
        return entity;
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForCreateTransaction(FieldDescriptor memoField) {
        List<UpdateEntityTestSpec> testSpecs = super.getUpdateEntityTestSpecsForCreateTransaction(memoField);

        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        FieldDescriptor field = getInnerBodyFieldDescriptorByName("max_automatic_token_associations");
        innerBody = innerBody.toBuilder().setField(field, 500).build();
        body = getTransactionBody(body, innerBody);

        AbstractEntity expected = getExpectedUpdatedEntity();
        expected.setMaxAutomaticTokenAssociations(500);
        expected.setMemo("");
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("create entity with non-zero max_automatic_token_associations")
                        .expected(expected)
                        .recordItem(getRecordItem(body, getDefaultTransactionRecord().build()))
                        .build()
        );

        return testSpecs;
    }

    @Test
    void updateTransactionStakedAccountId() {
        // given
        final AccountID accountId = AccountID.newBuilder().setAccountNum(1L).build();
        var recordItem = recordItemBuilder.cryptoCreate()
                .transactionBody(b -> b.setDeclineReward(false).setStakedAccountId(accountId))
                .build();

        // when
        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        // then
        verify(entityListener).onEntity(entities.capture());
        assertThat(entities.getValue())
                .isNotNull()
                .returns(false, Entity::isDeclineReward)
                .returns(accountId.getAccountNum(), Entity::getStakedAccountId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
    }

    @Test
    void updateTransactionStakedNodeId() {
        // given
        long nodeId = 1L;
        var recordItem = recordItemBuilder.cryptoCreate()
                .transactionBody(b -> b.setDeclineReward(true).setStakedNodeId(nodeId))
                .build();

        // when
        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        // then
        verify(entityListener).onEntity(entities.capture());
        assertThat(entities.getValue())
                .isNotNull()
                .returns(true, Entity::isDeclineReward)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(-1L, Entity::getStakedAccountId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
    }

    @Test
    void updateAlias() {
        var alias = UtilityTest.ALIAS_ECDSA_SECP256K1;
        var recordItem = recordItemBuilder.cryptoCreate().record(r -> r.setAlias(DomainUtils.fromBytes(alias))).build();

        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        verify(entityIdService).notify(entities.capture());
        assertThat(entities.getValue())
                .isNotNull()
                .returns(alias, Entity::getAlias)
                .returns(UtilityTest.EVM_ADDRESS, Entity::getEvmAddress);
    }

    private Transaction transaction(RecordItem recordItem) {
        var entityId = EntityId.of(recordItem.getRecord().getReceipt().getAccountID());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(entityId))
                .get();
    }
}
