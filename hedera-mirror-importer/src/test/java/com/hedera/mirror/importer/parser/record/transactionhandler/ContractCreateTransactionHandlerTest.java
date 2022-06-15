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
import static org.mockito.Mockito.doReturn;
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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import com.hedera.mirror.common.domain.contract.Contract;
import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.AbstractEntity;
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
import com.hedera.mirror.importer.parser.record.ethereum.EthereumTransactionParser;
import com.hedera.mirror.importer.util.Utility;

class ContractCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

    private final EntityProperties entityProperties = new EntityProperties();

    @Captor
    private ArgumentCaptor<Contract> contracts;

    @Mock(lenient = true)
    protected EthereumTransactionParser ethereumTransactionParser;

    private RecordParserProperties recordParserProperties;

    @BeforeEach
    void beforeEach() {
        when(entityIdService.lookup(contractId)).thenReturn(EntityId.of(DEFAULT_ENTITY_NUM, CONTRACT));
    }

    @Override
    protected TransactionHandler getTransactionHandler() {
        recordParserProperties = new RecordParserProperties();
        return new ContractCreateTransactionHandler(entityIdService, entityListener, entityProperties,
                ethereumTransactionParser, recordParserProperties);
    }

    @Override
    protected TransactionBody.Builder getDefaultTransactionBody() {
        return TransactionBody.newBuilder()
                .setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance());
    }

    @Override
    protected AbstractEntity getExpectedUpdatedEntity() {
        AbstractEntity entity = super.getExpectedUpdatedEntity();
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
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        var autoRenewAccount = recordItem.getTransactionBody().getContractCreateInstance().getAutoRenewAccountId();
        when(entityIdService.lookup(autoRenewAccount)).thenReturn(EntityId.of(autoRenewAccount));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(autoRenewAccount.getAccountNum(), Contract::getAutoRenewAccountId)
                .returns(null, Contract::getEvmAddress)
                .satisfies(c -> assertThat(c.getFileId().getId()).isPositive())
                .returns(null, Contract::getInitcode)
        );
    }

    @Test
    void updateTransactionSuccessfulWithEvmAddressAndInitcode() {
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId()
                        .clearFileID()
                        .setInitcode(recordItemBuilder.bytes(2048)))
                .record(r -> r.getContractCreateResultBuilder().setEvmAddress(recordItemBuilder.evmAddress()))
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .satisfies(c -> assertThat(c.getEvmAddress()).hasSize(20))
                .returns(null, Contract::getFileId)
                .satisfies(c -> assertThat(c.getInitcode()).hasSize(2048))
        );
    }

    @Test
    void updateTransactionSuccessfulAutoRenewAccountAlias() {
        var alias = DomainUtils.fromBytes(domainBuilder.key());
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenReturn(EntityIdEndec.decode(10L, ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(10L, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getEvmAddress)
                .satisfies(c -> assertThat(c.getFileId().getId()).isPositive())
                .returns(null, Contract::getInitcode)
        );
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
        verify(entityListener).onContract(contracts.capture());
        assertThat(contracts.getValue())
                .isNotNull()
                .returns(false, Contract::getDeclineReward)
                .returns(accountID.getAccountNum(), Contract::getStakedAccountId)
                .returns(null, Contract::getStakedNodeId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Contract::getStakePeriodStart);
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
        verify(entityListener).onContract(contracts.capture());
        assertThat(contracts.getValue())
                .isNotNull()
                .returns(true, Contract::getDeclineReward)
                .returns(null, Contract::getStakedAccountId)
                .returns(nodeId, Contract::getStakedNodeId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Contract::getStakePeriodStart);
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
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.getAutoRenewAccountIdBuilder().setAlias(alias))
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction().
                customize(t -> t.consensusTimestamp(timestamp).entityId(contractId)).get();
        when(entityIdService.lookup(AccountID.newBuilder().setAlias(alias).build()))
                .thenThrow(new AliasNotFoundException("alias", ACCOUNT));
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getEvmAddress)
                .satisfies(c -> assertThat(c.getFileId().getId()).isPositive())
                .returns(null, Contract::getInitcode)
        );
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
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .parent(parentRecordItem)
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getFileId)
                .returns(null, Contract::getInitcode)
        );
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
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getRecord().getConsensusTimestamp()))
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getFileId)
                .returns(null, Contract::getInitcode)
        );
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
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getRecord().getConsensusTimestamp()))
                .parent(parentRecordItem)
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getFileId)
                .satisfies(c -> assertThat(c.getInitcode()).isNotEmpty())
        );
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
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getRecord().getConsensusTimestamp()))
                .parent(parentRecordItem)
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getInitcode)
                .satisfies(c -> assertThat(c.getFileId()).isNotNull())
        );
    }

    @Test
    void updateContractFromEthereumCreateWInitCodeParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.ethereumTransaction(true)
                .transactionBody(x -> x.clearCallData())
                .build();

        doReturn(domainBuilder.ethereumTransaction(true)
                .customize(x -> x.callDataId(null))
                .get()).when(ethereumTransactionParser).decode(any());

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearInitcode().clearFileID())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getRecord().getConsensusTimestamp()))
                .parent(parentRecordItem)
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getFileId)
                .satisfies(c -> assertThat(c.getInitcode()).isNotEmpty())
        );
    }

    @Test
    void updateContractFromEthereumCreateWFileIDParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.ethereumTransaction(true)
                .build();

        doReturn(domainBuilder.ethereumTransaction(false)
                .customize(x -> x.callDataId(null))
                .get()).when(ethereumTransactionParser).decode(any());

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearFileID().clearInitcode())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getRecord().getConsensusTimestamp()))
                .parent(parentRecordItem)
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getInitcode)
                .satisfies(c -> assertThat(c.getFileId()).isNotNull())
        );
    }

    @Test
    void updateContractFromEthereumCallWCallDataFileParent() {
        // parent item
        var parentRecordItem = recordItemBuilder.ethereumTransaction(false)
                .build();

        doReturn(domainBuilder.ethereumTransaction(true)
                .customize(x -> x.callDataId(null))
                .get()).when(ethereumTransactionParser).decode(any());

        // child item
        var recordItem = recordItemBuilder.contractCreate()
                .transactionBody(b -> b.clearAutoRenewAccountId().clearInitcode().clearFileID())
                .record(x -> x.setParentConsensusTimestamp(parentRecordItem.getRecord().getConsensusTimestamp()))
                .parent(parentRecordItem)
                .build();
        var contractId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var timestamp = recordItem.getConsensusTimestamp();
        var transaction = domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(timestamp).entityId(contractId))
                .get();
        transactionHandler.updateTransaction(transaction, recordItem);
        assertContract(contractId, timestamp, t -> assertThat(t)
                .returns(null, Contract::getAutoRenewAccountId)
                .returns(null, Contract::getFileId)
                .satisfies(c -> assertThat(c.getInitcode()).isNotEmpty())
        );
    }

    private void assertContract(EntityId contractId, long timestamp, Consumer<Contract> extraAssert) {
        verify(entityListener).onContract(contracts.capture());
        assertThat(contracts.getValue())
                .isNotNull()
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .returns(timestamp, Contract::getCreatedTimestamp)
                .returns(false, Contract::getDeleted)
                .returns(null, Contract::getExpirationTimestamp)
                .returns(contractId.getId(), Contract::getId)
                .satisfies(c -> assertThat(c.getKey()).isNotEmpty())
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .returns(contractId.getEntityNum(), Contract::getNum)
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive())
                .satisfies(c -> assertThat(c.getPublicKey()).isNotEmpty())
                .returns(contractId.getRealmNum(), Contract::getRealm)
                .returns(contractId.getShardNum(), Contract::getShard)
                .returns(CONTRACT, Contract::getType)
                .returns(Range.atLeast(timestamp), Contract::getTimestampRange)
                .returns(null, Contract::getObtainerId)
                .satisfies(extraAssert);
    }

    private Transaction transaction(RecordItem recordItem) {
        var entityId = EntityId.of(recordItem.getRecord().getReceipt().getContractID());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(entityId))
                .get();
    }
}
