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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.AbstractObjectAssert;
import org.junit.jupiter.api.Test;

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

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new CryptoCreateTransactionHandler(entityIdService, new RecordParserProperties());
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
        entity.setBalance(0L);
        entity.setDeclineReward(false);
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
        var stakedAccountId = AccountID.newBuilder().setAccountNum(1L).build();
        var recordItem = recordItemBuilder.cryptoCreate()
                .transactionBody(b -> b.setDeclineReward(false).setStakedAccountId(stakedAccountId))
                .build();
        var accountId = EntityId.of(recordItem.getRecord().getReceipt().getAccountID());

        // when
        var actualEntity = transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        // then
        assertEntity(actualEntity, accountId, recordItem.getConsensusTimestamp())
                .returns(false, Entity::getDeclineReward)
                .returns(stakedAccountId.getAccountNum(), Entity::getStakedAccountId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
    }

    @Test
    void updateTransactionStakedNodeId() {
        // given
        long nodeId = 1L;
        var recordItem = recordItemBuilder.cryptoCreate()
                .transactionBody(b -> b.setDeclineReward(true).setStakedNodeId(nodeId))
                .build();
        var accountId = EntityId.of(recordItem.getRecord().getReceipt().getAccountID());

        // when
        var actualEntity = transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        // then
        assertEntity(actualEntity, accountId, recordItem.getConsensusTimestamp())
                .returns(true, Entity::getDeclineReward)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(null, Entity::getStakedAccountId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
    }

    @Test
    void updateAlias() {
        var alias = UtilityTest.ALIAS_ECDSA_SECP256K1;
        var recordItem = recordItemBuilder.cryptoCreate().record(r -> r.setAlias(DomainUtils.fromBytes(alias))).build();
        var accountId = EntityId.of(recordItem.getRecord().getReceipt().getAccountID());

        var actualEntity = transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        assertEntity(actualEntity, accountId, recordItem.getConsensusTimestamp())
                .returns(alias, Entity::getAlias)
                .returns(UtilityTest.EVM_ADDRESS, Entity::getEvmAddress);
    }

    private AbstractObjectAssert<?, Entity> assertEntity(Optional<Entity> actualEntity, EntityId accountId,
                                                         long timestamp) {
        verify(entityListener, never()).onEntity(any(Entity.class));
        return assertThat(actualEntity)
                .get()
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(0L, Entity::getBalance)
                .returns(false, Entity::getDeleted)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(accountId.getId(), Entity::getId)
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .returns(accountId.getEntityNum(), Entity::getNum)
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .returns(accountId.getRealmNum(), Entity::getRealm)
                .returns(accountId.getShardNum(), Entity::getShard)
                .returns(ACCOUNT, Entity::getType)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                .returns(null, Entity::getObtainerId);
    }

    private Transaction transaction(RecordItem recordItem) {
        var entityId = EntityId.of(recordItem.getRecord().getReceipt().getAccountID());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(entityId))
                .get();
    }
}
