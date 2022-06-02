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
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.BoolValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.parser.PartialDataAction;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.util.Utility;

class ContractUpdateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private RecordParserProperties recordParserProperties;

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(ContractID.getDefaultInstance(), contractId))
                .thenReturn(EntityId.of(DEFAULT_ENTITY_NUM, CONTRACT));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        recordParserProperties = new RecordParserProperties();
        return new ContractUpdateTransactionHandler(entityIdService, entityListener, recordParserProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractUpdateInstance(ContractUpdateTransactionBody.newBuilder()
                        .setContractID(ContractID.newBuilder().setContractNum(DEFAULT_ENTITY_NUM).build()));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return EntityType.CONTRACT;
    }

    @Test
    void testGetEntityIdReceipt() {
        var recordItem = recordItemBuilder.contractUpdate().build();
        ContractID contractIdBody = recordItem.getTransactionBody().getContractUpdateInstance().getContractID();
        ContractID contractIdReceipt = recordItem.getRecord().getReceipt().getContractID();
        EntityId expectedEntityId = EntityId.of(contractIdReceipt);

        when(entityIdService.lookup(contractIdReceipt, contractIdBody)).thenReturn(expectedEntityId);
        EntityId entityId = transactionHandler.getEntity(recordItem);
        assertThat(entityId).isEqualTo(expectedEntityId);
    }

    @Test
    void updateTransactionUnsuccessful() {
        var recordItem = recordItemBuilder.contractUpdate()
                .receipt(r -> r.setStatus(ResponseCodeEnum.INSUFFICIENT_TX_FEE)).build();
        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder.contractUpdate().build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(EntityIdEndec.decode(10L, ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(10L, Contract::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive())
        );
    }

    @Test
    void updateTransactionWithNewStakedAccountId() {
        AccountID accountId = AccountID.newBuilder().setAccountNum(1L).build();
        RecordItem withStakedNodeIdSet = recordItemBuilder.contractUpdate()
                .transactionBody(body -> body.clear().setStakedAccountId(accountId))
                .build();
        setupForContractUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(1L, Contract::getStakedAccountId)
                .returns(false, Contract::isDeclineReward)
                .returns(-1L, Contract::getStakedNodeId)
                .returns(Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()),
                        Contract::getStakePeriodStart)
        );
    }

    @Test
    void updateTransactionStartNewStakingPeriodAfterDeclineRewardChanged() {
        RecordItemBuilder recordItemBuilder = new RecordItemBuilder();
        RecordItem withDeclineValueSet = recordItemBuilder.contractUpdate()
                .transactionBody(body -> body.setDeclineReward(BoolValue.of(false))
                        .clearStakedAccountId()
                        .clearStakedNodeId())
                .build();
        setupForContractUpdateTransactionTest(withDeclineValueSet, t -> assertThat(t)
                .returns(false, Contract::isDeclineReward)
                // since the contract is not being saved in the database,
                // it does not have the default values of -1 for the staking fields.
                .returns(null, Contract::getStakedNodeId)
                .returns(null, Contract::getStakedAccountId)
                .returns(Utility.getEpochDay(withDeclineValueSet.getConsensusTimestamp()),
                        Contract::getStakePeriodStart)
        );
    }

    @Test
    void updateTransactionStartNewStakingPeriodAfterNodeIdChanged() {
        RecordItem withStakedNodeIdSet = recordItemBuilder.contractUpdate()
                .transactionBody(body -> body.setStakedNodeId(1L))
                .build();
        setupForContractUpdateTransactionTest(withStakedNodeIdSet, t -> assertThat(t)
                .returns(1L, Contract::getStakedNodeId)
                .returns(-1L, Contract::getStakedAccountId)
                .returns(true, Contract::isDeclineReward)
                .returns(Utility.getEpochDay(withStakedNodeIdSet.getConsensusTimestamp()),
                        Contract::getStakePeriodStart)
        );
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.contractUpdate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(EntityIdEndec.decode(10L, ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(10L, Contract::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive())
        );
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartialDataAction.class, names = {"DEFAULT", "ERROR"})
    void updateTransactionThrowsWithAliasNotFound(PartialDataAction partialDataAction) {
        // given
        recordParserProperties.setPartialDataAction(partialDataAction);
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.contractUpdate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenThrow(new AliasNotFoundException("alias", ACCOUNT));

        // when, then
        assertThrows(AliasNotFoundException.class, () -> transactionHandler.updateTransaction(transaction, recordItem));
    }

    @Test
    void updateTransactionWithAliasNotFoundAndPartialDataActionSkip() {
        recordParserProperties.setPartialDataAction(PartialDataAction.SKIP);
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.contractUpdate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenThrow(new AliasNotFoundException("alias", ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive())
        );
    }

    @Test
    void updateTransactionSuccessfulClearAutoRenewAccountId() {
        var recordItem = recordItemBuilder.contractUpdate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAccountNum(0))
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(0L, Contract::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .satisfies(c -> assertThat(c.getExpirationTimestamp()).isPositive())
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive())
        );
    }

    @Test
    void updateTransactionSuccessfulWithNoUpdate() {
        var recordItem = recordItemBuilder.contractUpdate()
                .transactionBody(b -> {
                    var contractId = b.getContractID();
                    b.clear().setContractID(contractId);
                })
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewPeriod)
                .returns(null, Contract::getExpirationTimestamp)
                .returns(null, Contract::getKey)
                .returns(null, Contract::getMaxAutomaticTokenAssociations)
                .returns(null, Contract::getMemo)
                .returns(null, Contract::getProxyAccountId)
                .returns(null, Contract::getPublicKey)
        );
    }

    private void assertContractUpdate(long timestamp, EntityId contractId, Consumer<Contract> extraAssert) {
        verify(entityListener, times(1)).onContract(assertArg(t -> assertAll(
                () -> assertThat(t)
                        .isNotNull()
                        .returns(null, Contract::getCreatedTimestamp)
                        .returns(false, Contract::getDeleted)
                        .returns(null, Contract::getEvmAddress)
                        .returns(null, Contract::getFileId)
                        .returns(contractId.getId(), Contract::getId)
                        .returns(null, Contract::getInitcode)
                        .returns(contractId.getEntityNum(), Contract::getNum)
                        .returns(null, Contract::getObtainerId)
                        .returns(contractId.getRealmNum(), Contract::getRealm)
                        .returns(contractId.getShardNum(), Contract::getShard)
                        .returns(Range.atLeast(timestamp), Contract::getTimestampRange)
                        .returns(CONTRACT, Contract::getType),
                () -> extraAssert.accept(t)
        )));
    }

    private void setupForContractUpdateTransactionTest(RecordItem recordItem, Consumer<Contract> extraAssertions) {
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp)
                        .entityId(contractId)
                )
                .get();
        when(entityIdService.lookup(any(AccountID.class))).thenReturn(EntityIdEndec.decode(10L, ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContractUpdate(timestamp, contractId, extraAssertions);
    }
}
