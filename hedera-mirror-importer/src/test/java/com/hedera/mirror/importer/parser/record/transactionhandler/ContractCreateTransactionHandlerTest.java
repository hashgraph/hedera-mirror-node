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

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityIdEndec;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.parser.PartialDataAction;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;

class ContractCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EntityProperties entityProperties = new EntityProperties();

    @Captor
    private ArgumentCaptor<Contract> contracts;

    private RecordParserProperties recordParserProperties;

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(contractId)).thenReturn(EntityId.of(DEFAULT_ENTITY_NUM, CONTRACT));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        recordParserProperties = new RecordParserProperties();
        return new ContractCreateTransactionHandler(entityIdService, entityListener, entityProperties,
                recordParserProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance());
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
    protected TransactionReceipt.Builder getTransactionReceipt(ResponseCodeEnum responseCodeEnum) {
        return TransactionReceipt.newBuilder().setStatus(responseCodeEnum).setContractID(contractId);
    }

    @Override
    protected List<UpdateEntityTestSpec> getUpdateEntityTestSpecsForCreateTransaction(
            Descriptors.FieldDescriptor memoField) {
        List<UpdateEntityTestSpec> testSpecs = super.getUpdateEntityTestSpecsForCreateTransaction(memoField);

        TransactionBody body = getTransactionBodyForUpdateEntityWithoutMemo();
        Message innerBody = getInnerBody(body);
        body = getTransactionBody(body, innerBody);
        byte[] evmAddress = TestUtils.generateRandomByteArray(20);
        var contractCreateResult = ContractFunctionResult.newBuilder()
                .setEvmAddress(BytesValue.of(ByteString.copyFrom(evmAddress)));
        var recordBuilder = getDefaultTransactionRecord().setContractCreateResult(contractCreateResult);

        AbstractEntity expected = getExpectedUpdatedEntity();
        expected.setEvmAddress(evmAddress);
        expected.setMemo("");
        testSpecs.add(
                UpdateEntityTestSpec.builder()
                        .description("create contract entity with evm address in record")
                        .expected(expected)
                        .recordItem(getRecordItem(body, recordBuilder.build()))
                        .build()
        );

        return testSpecs;
    }

    @Override
    protected TransactionRecord.Builder getDefaultTransactionRecord() {
        return super.getDefaultTransactionRecord()
                .setContractCreateResult(ContractFunctionResult.newBuilder().addCreatedContractIDs(contractId));
    }

    @Override
    protected EntityType getExpectedEntityIdType() {
        return CONTRACT;
    }

    @Test
    void updateContractResultEmptyContractCallFunctionParams() {
        ContractResult contractResult = new ContractResult();
        var recordItem = recordItemBuilder.contractCreate().build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        var transaction = recordItem.getTransactionBody().getContractCreateInstance();
        assertThat(contractResult)
                .returns(transaction.getInitialBalance(), ContractResult::getAmount)
                .returns(transaction.getGas(), ContractResult::getGasLimit)
                .returns(null, ContractResult::getFailedInitcode)
                .returns(DomainUtils.toBytes(transaction.getConstructorParameters()),
                        ContractResult::getFunctionParameters);
    }

    @Test
    void updateContractResultFailedCreateTransaction() {
        var contractResult = new ContractResult();
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(t -> t.setInitcode(ByteString.copyFrom(new byte[] {9, 8, 7})))
                .record(TransactionRecord.Builder::clearContractCreateResult)
                .receipt(r -> r.clearContractID().setStatus(ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION))
                .build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        var transaction = recordItem.getTransactionBody().getContractCreateInstance();
        assertThat(contractResult)
                .returns(transaction.getInitialBalance(), ContractResult::getAmount)
                .returns(transaction.getGas(), ContractResult::getGasLimit)
                .returns(DomainUtils.toBytes(transaction.getInitcode()), ContractResult::getFailedInitcode)
                .returns(DomainUtils.toBytes(transaction.getConstructorParameters()),
                        ContractResult::getFunctionParameters);
    }

    @Test
    void updateContractResultNonContractCallTransaction() {
        ContractResult contractResult = ContractResult.builder().build();
        var recordItem = recordItemBuilder.contractCall().build();
        transactionHandler.updateContractResult(contractResult, recordItem);

        assertThat(contractResult)
                .returns(null, ContractResult::getAmount)
                .returns(null, ContractResult::getGasLimit)
                .returns(null, ContractResult::getFailedInitcode)
                .returns(null, ContractResult::getFunctionParameters);
    }

    @Test
    void updateTransactionUnsuccessful() {
        var recordItem = recordItemBuilder.contractCreate()
                .receipt(r -> r.setStatus(ResponseCodeEnum.INSUFFICIENT_GAS))
                .build();
        var transaction = new Transaction();
        transactionHandler.updateTransaction(transaction, recordItem);
        verifyNoInteractions(entityListener);
    }

    @Test
    void updateTransactionSuccessful() {
        var recordItem = recordItemBuilder.contractCreate().build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var autoRenewAccount = recordItem.getTransactionBody().getContractCreateInstance().getAutoRenewAccountId();
        var initCode = DomainUtils.toBytes(recordItem.getSidecarRecords().get(2).getBytecode().getInitcode());
        when(entityIdService.lookup(autoRenewAccount)).thenReturn(EntityId.of(autoRenewAccount));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(autoRenewAccount.getAccountNum(), Entity::getAutoRenewAccountId)
                .returns(null, Entity::getEvmAddress);
        assertContract(contractId)
                .satisfies(c -> assertThat(c.getFileId().getId()).isPositive())
                .returns(initCode, Contract::getInitcode);
    }

    @Test
    void updateTransactionSuccessfulWithEvmAddressAndInitcode() {
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId()
                        .clearFileID()
                        .setInitcode(recordItemBuilder.bytes(2048)))
                .record(r -> r.getContractCreateResultBuilder().setEvmAddress(recordItemBuilder.evmAddress()))
                .build();
        var contractEntityId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractEntityId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractEntityId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getEvmAddress()).hasSize(20));
        assertContract(contractEntityId)
                .returns(null, Contract::getFileId)
                .satisfies(c -> assertThat(c.getInitcode()).hasSize(2048));
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        var initCode = DomainUtils.toBytes(recordItem.getSidecarRecords().get(2).getBytecode().getInitcode());
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(EntityIdEndec.decode(10L, ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(10L, Entity::getAutoRenewAccountId)
                .returns(null, Entity::getEvmAddress);
        assertContract(contractId)
                .satisfies(c -> assertThat(c.getFileId().getId()).isPositive())
                .returns(initCode, Contract::getInitcode);
    }

    @Test
    void updateTransactionStakedAccountId() {
        // given
        final AccountID accountID = AccountID.newBuilder().setAccountNum(1L).build();
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().setDeclineReward(false).setStakedAccountId(accountID))
                .build();

        // when
        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        // then
        verify(entityListener).onEntity(entityCaptor.capture());
        assertThat(entityCaptor.getValue())
                .isNotNull()
                .returns(false, Entity::getDeclineReward)
                .returns(accountID.getAccountNum(), Entity::getStakedAccountId)
                .returns(null, Entity::getStakedNodeId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
    }

    @Test
    void updateTransactionStakedNodeId() {
        // given
        var nodeId = 1L;
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().setDeclineReward(true).setStakedNodeId(nodeId))
                .build();

        // when
        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        // then
        verify(entityListener).onEntity(entityCaptor.capture());
        assertThat(entityCaptor.getValue())
                .isNotNull()
                .returns(true, Entity::getDeclineReward)
                .returns(null, Entity::getStakedAccountId)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartialDataAction.class, names = {"DEFAULT", "ERROR"})
    void updateTransactionThrowsWithAliasNotFound(PartialDataAction partialDataAction) {
        // given
        recordParserProperties.setPartialDataAction(partialDataAction);
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
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
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        var initCode = DomainUtils.toBytes(recordItem.getSidecarRecords().get(2).getBytecode().getInitcode());
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenThrow(new AliasNotFoundException("alias", ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId)
                .returns(null, Entity::getEvmAddress);
        assertContract(contractId)
                .satisfies(c -> assertThat(c.getFileId().getId()).isPositive())
                .returns(initCode, Contract::getInitcode);
    }

    @Test
    void updateContractFromContractCreateParentNotAChild() {
        // parent item
        var parentRecordItem = recordItemBuilder.contractCreate()
                .transactionBody(x -> x.clearFileID()
                        .setInitcode(ByteString.copyFrom("init code", StandardCharsets.UTF_8)))
                .build();

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .recordItem(r -> r.parent(parentRecordItem))
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem.getSidecarRecords().get(2).getBytecode().getInitcode());
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId)
                .returns(null, Contract::getFileId)
                .returns(initCode, Contract::getInitcode);
    }

    @Test
    void updateContractFromContractCreateNoParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.contractCreate()
                .transactionBody(x -> x.clearFileID()
                        .setInitcode(ByteString.copyFrom("init code", StandardCharsets.UTF_8)))
                .build();

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem.getSidecarRecords().get(2).getBytecode().getInitcode());
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId)
                .returns(null, Contract::getFileId)
                .returns(initCode, Contract::getInitcode);
    }

    @Test
    void updateContractFromContractCreateWInitCodeParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.contractCreate()
                .transactionBody(x -> x.clearFileID()
                        .setInitcode(ByteString.copyFrom("init code", StandardCharsets.UTF_8)))
                .build();

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.parent(parentRecordItem))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId)
                .returns(null, Contract::getFileId)
                .satisfies(c -> assertThat(c.getInitcode()).isNotEmpty());
    }

    @Test
    void updateContractFromContractCreateWFileIDParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.contractCreate()
                .transactionBody(x -> x.clearInitcode()
                        .setFileID(FileID.newBuilder().setFileNum(DEFAULT_ENTITY_NUM).build()))
                .build();

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearInitcode().clearFileID())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.parent(parentRecordItem))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem.getSidecarRecords().get(2).getBytecode().getInitcode());
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId)
                .returns(initCode, Contract::getInitcode)
                .satisfies(c -> assertThat(c.getFileId()).isNotNull());
    }

    @Test
    void updateContractFromEthereumCreateWInitCodeParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.ethereumTransaction(true)
                .transactionBody(x -> x.clearCallData())
                .build();

        var ethereumTransaction = domainBuilder.ethereumTransaction(true)
                .customize(x -> x.callDataId(null))
                .get();

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearInitcode().clearFileID())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction).parent(parentRecordItem))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId)
                .returns(null, Contract::getFileId)
                .satisfies(c -> assertThat(c.getInitcode()).isNotEmpty());
    }

    @Test
    void updateContractFromEthereumCreateWFileIDParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.ethereumTransaction(true)
                .build();

        var ethereumTransaction = domainBuilder.ethereumTransaction(false)
                .customize(x -> x.callDataId(null))
                .get();

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction).parent(parentRecordItem))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var initCode = DomainUtils.toBytes(recordItem.getSidecarRecords().get(2).getBytecode().getInitcode());
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId)
                .returns(initCode, Contract::getInitcode)
                .satisfies(c -> assertThat(c.getFileId()).isNotNull());
    }

    @Test
    void updateContractFromEthereumCallWCallDataFileParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.ethereumTransaction(false)
                .build();

        var ethereumTransaction = domainBuilder.ethereumTransaction(true)
                .customize(x -> x.callDataId(null))
                .get();

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearInitcode().clearFileID())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getTransactionRecord().getConsensusTimestamp()))
                .recordItem(r -> r.ethereumTransaction(ethereumTransaction).parent(parentRecordItem))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertEntity(contractId, timestamp)
                .returns(null, Entity::getAutoRenewAccountId);
        assertContract(contractId)
                .returns(null, Contract::getFileId)
                .satisfies(c -> assertThat(c.getInitcode()).isNotEmpty());
    }

    @Test
    void migrationBytecodeNotProcessed() {
        var recordItem = recordItemBuilder.contractCreate()
                .sidecarRecords(r -> r.get(2).setMigration(true))
                .build();
        var contractId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var autoRenewAccount = recordItem.getTransactionBody().getContractCreateInstance().getAutoRenewAccountId();
        when(entityIdService.lookup(autoRenewAccount)).thenReturn(EntityId.of(autoRenewAccount));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId)
                .returns(null, Contract::getInitcode)
                .returns(null, Contract::getRuntimeBytecode);
    }

    private ObjectAssert<Contract> assertContract(EntityId contractId) {
        verify(entityListener).onContract(contracts.capture());
        return assertThat(contracts.getAllValues())
                .hasSize(1)
                .first()
                .returns(contractId.getId(), Contract::getId);
    }

    private ObjectAssert<Entity> assertEntity(EntityId contractId, long timestamp) {
        verify(entityListener).onEntity(entityCaptor.capture());
        return assertThat(entityCaptor.getValue())
                .isNotNull()
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(0L, Entity::getBalance)
                .returns(false, Entity::getDeleted)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(contractId.getId(), Entity::getId)
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .returns(contractId.getEntityNum(), Entity::getNum)
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .returns(contractId.getRealmNum(), Entity::getRealm)
                .returns(contractId.getShardNum(), Entity::getShard)
                .returns(CONTRACT, Entity::getType)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                .returns(null, Entity::getObtainerId);
    }

    private Transaction transaction(RecordItem recordItem) {
        var entityId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getContractID());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(entityId))
                .get();
    }
}
