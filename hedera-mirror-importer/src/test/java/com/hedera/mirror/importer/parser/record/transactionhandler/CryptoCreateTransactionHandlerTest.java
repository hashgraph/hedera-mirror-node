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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.ObjectAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.parser.domain.RecordItemBuilder;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.util.Utility;
import com.hedera.mirror.importer.util.UtilityTest;

class CryptoCreateTransactionHandlerTest extends AbstractTransactionHandlerTest {

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
        var accountId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        // when
        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        // then
        assertEntity(accountId, recordItem.getConsensusTimestamp())
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
        var accountId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        // when
        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        // then
        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(true, Entity::getDeclineReward)
                .returns(nodeId, Entity::getStakedNodeId)
                .returns(null, Entity::getStakedAccountId)
                .returns(Utility.getEpochDay(recordItem.getConsensusTimestamp()), Entity::getStakePeriodStart);
    }

    @Test
    void updateAlias() {
        var alias = UtilityTest.ALIAS_ECDSA_SECP256K1;
        var recordItem = recordItemBuilder.cryptoCreate().record(r -> r.setAlias(DomainUtils.fromBytes(alias))).build();
        var accountId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(alias, Entity::getAlias);
    }

    @Test
    void updateAliasEvmKey() {
        var alias = UtilityTest.ALIAS_ECDSA_SECP256K1;
        var evmAddress = UtilityTest.EVM_ADDRESS;
        var recordItem = recordItemBuilder.cryptoCreate()
                .record(r -> r.setEvmAddress(DomainUtils.fromBytes(evmAddress)))
                .transactionBody(t -> t
                        .setAlias(DomainUtils.fromBytes(alias))
                        .setKey(Key.getDefaultInstance())
                ).build();
        var accountId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(alias, Entity::getAlias)
                .returns(alias, Entity::getKey)
                .returns(evmAddress, Entity::getEvmAddress);
    }

    private static Stream<Arguments> provideAlias() {
        var validKey = Key.newBuilder()
                .setECDSASecp256K1(ByteString.copyFrom(TestUtils.generateRandomByteArray(20))).build();
        var emptyKey = Key.getDefaultInstance();
        var validAliasForKey = ByteString.copyFrom(UtilityTest.ALIAS_ECDSA_SECP256K1);
        var invalidAliasForKey = ByteString.fromHex("1234");
        return Stream.of(
                Arguments.of(validAliasForKey, validKey, validKey.toByteArray()),
                Arguments.of(validAliasForKey, emptyKey, validAliasForKey.toByteArray()),
                Arguments.of(invalidAliasForKey, validKey, validKey.toByteArray()),
                Arguments.of(invalidAliasForKey, emptyKey, null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideAlias")
    void updateKeyFromTransactionBody(ByteString alias, Key key, byte[] expectedKey) {
        var recordItem = recordItemBuilder.cryptoCreate()
                .record(r -> r.setAlias(alias))
                .transactionBody(t -> t.setKey(key))
                .build();
        var accountId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(expectedKey, Entity::getKey);
    }

    private static Stream<Arguments> provideEvmAddresses() {
        var evmAddressByteString = ByteString.copyFrom(UtilityTest.EVM_ADDRESS);
        var evmAddressTwo = RecordItemBuilder.EVM_ADDRESS;
        return Stream.of(
                Arguments.of(ByteString.empty(), ByteString.empty(), UtilityTest.EVM_ADDRESS),
                Arguments.of(evmAddressByteString, ByteString.empty(), evmAddressByteString.toByteArray()),
                Arguments.of(ByteString.empty(), evmAddressByteString, evmAddressByteString.toByteArray()),
                Arguments.of(evmAddressByteString, evmAddressTwo, evmAddressByteString.toByteArray())
        );
    }

    @ParameterizedTest
    @MethodSource("provideEvmAddresses")
    void updateEvmAddress(ByteString recordEvmAddress, ByteString transactionBodyEvmAddress, byte[] expected) {
        var recordItem = recordItemBuilder.cryptoCreate()
                .record(r -> r.setEvmAddress(recordEvmAddress))
                .transactionBody(t -> t
                        .setAlias(ByteString.copyFrom(UtilityTest.ALIAS_ECDSA_SECP256K1))
                        .setEvmAddress(transactionBodyEvmAddress))
                .build();
        var accountId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());

        transactionHandler.updateTransaction(transaction(recordItem), recordItem);

        assertEntity(accountId, recordItem.getConsensusTimestamp())
                .returns(expected, Entity::getEvmAddress);
    }

    private ObjectAssert<Entity> assertEntity(EntityId accountId, long timestamp) {
        verify(entityListener).onEntity(entityCaptor.capture());
        return assertThat(entityCaptor.getValue())
                .isNotNull()
                .satisfies(c -> assertThat(c.getAutoRenewPeriod()).isPositive())
                .returns(timestamp, Entity::getCreatedTimestamp)
                .returns(0L, Entity::getBalance)
                .returns(false, Entity::getDeleted)
                .returns(null, Entity::getExpirationTimestamp)
                .returns(accountId.getId(), Entity::getId)
                .satisfies(c -> assertThat(c.getMaxAutomaticTokenAssociations()).isPositive())
                .satisfies(c -> assertThat(c.getMemo()).isNotEmpty())
                .returns(accountId.getEntityNum(), Entity::getNum)
                .satisfies(c -> assertThat(c.getProxyAccountId().getId()).isPositive())
                .returns(accountId.getRealmNum(), Entity::getRealm)
                .returns(accountId.getShardNum(), Entity::getShard)
                .returns(ACCOUNT, Entity::getType)
                .returns(Range.atLeast(timestamp), Entity::getTimestampRange)
                .returns(null, Entity::getObtainerId);
    }

    private Transaction transaction(RecordItem recordItem) {
        var entityId = EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        return domainBuilder.transaction()
                .customize(t -> t.consensusTimestamp(consensusTimestamp).entityId(entityId))
                .get();
    }
}
