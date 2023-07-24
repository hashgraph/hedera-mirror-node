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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ContractUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(ContractID.getDefaultInstance(), contractId))
                .thenReturn(Optional.of(EntityId.of(DEFAULT_ENTITY_NUM, CONTRACT)));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        return new ContractUpdateTransactionHandler(entityIdService, entityListener);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder()
                                .setContractNum(DEFAULT_ENTITY_NUM)
                                .build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Test
    void testGetEntityIdReceipt() {
        var recordItem = recordItemBuilder.contractUpdate().build();
        ContractID contractIdBody =
                recordItem.getTransactionBody().getContractUpdateInstance().getContractID();
        ContractID contractIdReceipt =
                recordItem.getTransactionRecord().getReceipt().getContractID();
        EntityId expectedEntityId = EntityId.of(contractIdReceipt);

        when(entityIdService.lookup(contractIdReceipt, contractIdBody)).thenReturn(Optional.of(expectedEntityId));
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }

    @Test
    void updateTransactionSuccessful() {
        // given
        var recordItem = recordItemBuilder.contractUpdate().build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasAccount =
                recordItem.getTransactionBody().getContractUpdateInstance().getAutoRenewAccountId();
        var aliasAccountId = EntityId.of(10L, ACCOUNT);
        var expectedEntityTransactions = getExpectedEntityTransactions(aliasAccountId, recordItem, transaction);
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.of(aliasAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(10L, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive()));
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expectedEntityTransactions);
    }

    @Test
    void updateTransactionSuccessfulWhenEntityTransactionDisabled() {
        var recordItem = recordItemBuilder
                .contractUpdate()
                .entityTransactionPredicate(e -> false)
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasAccount =
                recordItem.getTransactionBody().getContractUpdateInstance().getAutoRenewAccountId();
        var aliasAccountId = EntityId.of(10L, ACCOUNT);
        when(entityIdService.lookup(aliasAccount)).thenReturn(Optional.of(aliasAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(10L, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive()));
        assertThat(recordItem.getEntityTransactions()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 100})
    void updateTransactionStakedAccountId(long accountNum) {
        // Note, the sentinel value '0.0.0' clears the staked account id, in importer, we persist the encoded id '0' to
        // db to indicate there is no staked account id
        AccountID accountId = AccountID.newBuilder().setAccountNum(accountNum).build();
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .contractUpdate()
                .transactionBody(body -> body.setStakedAccountId(accountId).clearDeclineReward())
                .build();
        setupForContractUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(accountNum, Entity::getStakedAccountId)
                .returns(null, Entity::getDeclineReward)
                .returns(-1L, Entity::getStakedNodeId)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void updateTransactionDeclineReward(Boolean declineReward) {
        RecordItem withDeclineValueSet = recordItemBuilder
                .contractUpdate()
                .transactionBody(body -> body.setDeclineReward(BoolValue.of(declineReward))
                        .clearStakedAccountId()
                        .clearStakedNodeId())
                .build();
        setupForContractUpdateTransactionTest(withDeclineValueSet, t -> assertThat(t)
                .returns(declineReward, Entity::getDeclineReward)
                // since the contract is not being saved in the database,
                // it does not have the default values of -1 for the staking fields.
                .returns(null, Entity::getStakedNodeId)
                .returns(null, Entity::getStakedAccountId)
                .returns(
                        Utility.getEpochDay(withDeclineValueSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, -1})
    void updateTransactionStakedNodeId(Long nodeId) {
        RecordItem withStakedNodeIdSet = recordItemBuilder
                .contractUpdate()
                .transactionBody(body -> body.setStakedNodeId(nodeId))
                .build();
        setupForContractUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(0L, Entity::getStakedAccountId)
                .returns(true, Entity::getDeclineReward)
                .returns(
                        Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()), Entity::getStakePeriodStart));
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasAccountId = EntityId.of(10L, ACCOUNT);
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.of(aliasAccountId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(10L, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive()));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(aliasAccountId, recordItem, transaction));
    }

    @ParameterizedTest
    @MethodSource("provideEntities")
    void updateTransactionEntityNotFound(EntityId entityId) {
        // given
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(Optional.ofNullable(entityId));

        // when
        transactionHandler.updateTransaction(transaction, recordItem);

        // then
        var expectedAutoRenewAccountId = entityId == null ? null : entityId.getId();
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(expectedAutoRenewAccountId, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive()));
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(getExpectedEntityTransactions(null, recordItem, transaction));
    }

    @Test
    void updateTransactionSuccessfulClearAutoRenewAccountId() {
        var recordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAccountNum(0))
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(0L, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive()));
    }

    @Test
    void updateTransactionSuccessfulWithNoUpdate() {
        var recordItem = recordItemBuilder
                .contractUpdate()
                .transactionBody(b -> {
                    var contractId = b.getContractID();
                    b.clear().setContractID(contractId);
                })
                .build();
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(null, Entity::getAutoRenewPeriod)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(null, Entity::getKey)
                .returns(null, Entity::getMaxAutomaticTokenAssociations)
                .returns(null, Entity::getMemo)
                .returns(null, Entity::getProxyAccountId)
                .returns(null, Entity::getPublicKey));
    }

    private void assertContractUpdate(long timestamp, EntityId contractId, Consumer<Entity> extraAssert) {
        verify(entityListener, times(1))
                .onEntity(assertArg(t -> assertAll(
                        () -> assertThat(t)
                                .isNotNull()
                                .returns(null, Entity::getCreatedTimestamp)
                                .returns(false, Entity::getDeleted)
                                .returns(null, Entity::getEvmAddress)
                                .returns(contractId.getId(), Entity::getId)
                                .returns(contractId.getEntityNum(), Entity::getNum)
                                .returns(null, Entity::getObtainerId)
                                .returns(contractId.getRealmNum(), Entity::getRealm)
                                .returns(contractId.getShardNum(), Entity::getShard)
                                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                                .returns(CONTRACT, Entity::getType),
                        () -> extraAssert.accept(t))));
    }

    @SuppressWarnings("deprecation")
    private Map<Long, EntityTransaction> getExpectedEntityTransactions(
            EntityId autoRenewAccountId, RecordItem recordItem, Transaction transaction) {
        var body = recordItem.getTransactionBody().getContractUpdateInstance();
        if (EntityId.isEmpty(autoRenewAccountId)) {
            autoRenewAccountId = EntityId.of(body.getAutoRenewAccountId());
        }

        return getExpectedEntityTransactions(
                recordItem,
                transaction,
                autoRenewAccountId,
                EntityId.of(body.getStakedAccountId()),
                EntityId.of(body.getProxyAccountID()));
    }

    private void setupForContractUpdateTransactionTest(RecordItem recordItem, Consumer<Entity> extraAssertions) {
        var contractId =
                EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder
                .transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var aliasAccountId = EntityId.of(10L, ACCOUNT);
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(Optional.of(aliasAccountId));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, extraAssertions);
        assertThat(recordItem.getEntityTransactions())
                .containsExactlyInAnyOrderEntriesOf(
                        getExpectedEntityTransactions(aliasAccountId, recordItem, transaction));
    }
}
