package com.hedera.mirror.importer.repository;

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
import static com.hedera.mirror.common.domain.entity.EntityType.TOPIC;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.common.domain.balance.AccountBalance;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.TestUtils;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class EntityRepositoryTest extends AbstractRepositoryTest {

    private static final RowMapper<Entity> ROW_MAPPER = rowMapper(Entity.class);

    private final EntityRepository entityRepository;

    @Test
    void nullCharacter() {
        Entity entity = domainBuilder.entity().customize(e -> e.memo("abc" + (char) 0)).persist();
        assertThat(entityRepository.findById(entity.getId())).get().isEqualTo(entity);
    }

    @Test
    void publicKeyUpdates() {
        Entity entity = domainBuilder.entity().customize(b -> b.key(null)).persist();

        // unset key should result in null public key
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isNull();

        // default proto key of single byte should result in empty public key
        entity.setKey(Key.getDefaultInstance().toByteArray());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isEqualTo("");

        // invalid key should be null
        entity.setKey("123".getBytes());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isNull();

        // valid key should not be null
        entity.setKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("123")).build().toByteArray());
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isNotNull();

        // null key like unset should result in null public key
        entity.setKey(null);
        entityRepository.save(entity);
        assertThat(entityRepository.findById(entity.getId())).get()
                .extracting(Entity::getPublicKey).isNull();
    }

    /**
     * This test verifies that the Entity domain object and table definition are in sync with the entity_history table.
     */
    @Test
    void history() {
        Entity entity = domainBuilder.entity().persist();

        jdbcOperations.update("insert into entity_history select * from entity");
        List<Entity> entityHistory = jdbcOperations.query("select * from entity_history", ROW_MAPPER);

        assertThat(entityRepository.findAll()).containsExactly(entity);
        assertThat(entityHistory).containsExactly(entity);
    }

    @Test
    void findByAlias() {
        Entity entity = domainBuilder.entity().persist();
        byte[] alias = entity.getAlias();

        assertThat(entityRepository.findByAlias(alias)).get().isEqualTo(entity.getId());
    }

    @Test
    void findByEvmAddress() {
        Entity entity = domainBuilder.entity().persist();
        Entity entityDeleted = domainBuilder.entity().customize((b) -> b.deleted(true)).persist();
        assertThat(entityRepository.findByEvmAddress(entity.getEvmAddress())).get().isEqualTo(entity.getId());
        assertThat(entityRepository.findByEvmAddress(entityDeleted.getEvmAddress())).isEmpty();
        assertThat(entityRepository.findByEvmAddress(new byte[] {1, 2, 3})).isEmpty();
    }

    @Test
    void refreshEntityStateStart() {
        // given
        long epochDay = 1000L;
        long timestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        long previousTimestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay)) + 1000L;

        domainBuilder.nodeStake()
                .customize(ns -> ns.consensusTimestamp(previousTimestamp).epochDay(epochDay - 1))
                .persist();
        domainBuilder.nodeStake()
                .customize(ns -> ns.consensusTimestamp(timestamp).epochDay(epochDay))
                .persist();

        var account1 = domainBuilder.entity()
                .customize(e -> e.timestampRange(Range.atLeast(timestamp - 1)))
                .persist();
        var account2 = domainBuilder.entity()
                .customize(e -> e.deleted(null).timestampRange(Range.atLeast(timestamp - 2)))
                .persist();
        // account3 is valid after the node stake update timestamp
        var account3 = domainBuilder.entity()
                .customize(e -> e.timestampRange(Range.atLeast(timestamp + 1)))
                .persist();
        // history row for account3
        var account3History = domainBuilder.entityHistory()
                .customize(e -> e.id(account3.getId()).num(account3.getNum()).stakedNodeId(3L)
                        .timestampRange(Range.closedOpen(timestamp - 10, timestamp + 1)))
                .persist();
        // deleted account will not appear in entity_state_start
        var account4 = domainBuilder.entity()
                .customize(e -> e.deleted(true).timestampRange(Range.atLeast(timestamp - 3)))
                .persist(); // deleted
        // entity created after node stake timestamp will not appear in entity_state_start
        domainBuilder.entity().customize(e -> e.timestampRange(Range.atLeast(timestamp + 1))).persist();
        var contract = domainBuilder.entity()
                .customize(e -> e.stakedAccountId(null).stakedNodeId(null).stakePeriodStart(null)
                        .timestampRange(Range.atLeast(timestamp - 4)).type(CONTRACT))
                .persist();
        domainBuilder.entity().customize(e -> e.type(TOPIC).timestampRange(Range.atLeast(timestamp - 5))).persist();

        long balanceTimestamp = timestamp - 100L;
        domainBuilder.accountBalanceFile().customize(abf -> abf.consensusTimestamp(balanceTimestamp)).persist();
        domainBuilder.accountBalance()
                .customize(ab -> ab.balance(100L)
                        .id(new AccountBalance.Id(balanceTimestamp, account1.toEntityId())))
                .persist();
        domainBuilder.accountBalance()
                .customize(ab -> ab.balance(200L)
                        .id(new AccountBalance.Id(balanceTimestamp, account2.toEntityId())))
                .persist();
        domainBuilder.accountBalance()
                .customize(ab -> ab.balance(400L)
                        .id(new AccountBalance.Id(balanceTimestamp, account4.toEntityId())))
                .persist();
        domainBuilder.accountBalance()
                .customize(ab -> ab.balance(500L)
                        .id(new AccountBalance.Id(balanceTimestamp, contract.toEntityId())))
                .persist();
        // crypto transfer at balanceTimestamp, should be ignored for account1 balance calculation
        domainBuilder.cryptoTransfer()
                .customize(ct -> ct.amount(20L).consensusTimestamp(balanceTimestamp).entityId(account1.getId()))
                .persist();
        domainBuilder.cryptoTransfer()
                .customize(ct -> ct.amount(30L).consensusTimestamp(balanceTimestamp + 1).entityId(account1.getId()))
                .persist();
        domainBuilder.cryptoTransfer()
                .customize(ct -> ct.amount(-10L).consensusTimestamp(balanceTimestamp + 2).entityId(account2.getId()))
                .persist();
        // account3 is created after the account balance file
        domainBuilder.cryptoTransfer()
                .customize(ct -> ct.amount(123L).consensusTimestamp(account3History.getTimestampLower())
                        .entityId(account3History.getId()))
                .persist();

        var expectedAccount1 = account1.toBuilder().balance(130L).stakedAccountId(0L).build();
        var expectedAccount2 = account2.toBuilder().balance(190L).stakedAccountId(0L).build();
        var expectedAccount3 = account3.toBuilder().balance(123L).stakedAccountId(0L).stakedNodeId(3L).build();
        var expectedContract = contract.toBuilder().balance(500L).stakedAccountId(0L).stakedNodeId(-1L)
                .stakePeriodStart(-1L).build();

        // when
        entityRepository.refreshEntityStateStart();

        // then
        var fields = new String[] {"balance", "declineReward", "id", "stakedAccountId", "stakedNodeId",
                "stakePeriodStart"};
        assertThat(jdbcOperations.query("select * from entity_state_start", ROW_MAPPER))
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactlyInAnyOrder(expectedAccount1, expectedAccount2, expectedAccount3, expectedContract);

        // given
        expectedAccount1.setDeclineReward(true);
        expectedAccount1.setStakePeriodStart(10L);
        expectedAccount1.setStakedNodeId(2L);
        expectedAccount2.setStakedAccountId(account1.getId());
        expectedAccount2.setStakePeriodStart(10L);
        contract.setDeleted(true);
        entityRepository.saveAll(List.of(expectedAccount1, expectedAccount2, contract));

        // when
        entityRepository.refreshEntityStateStart();

        // then
        assertThat(jdbcOperations.query("select * from entity_state_start", ROW_MAPPER))
                .usingRecursiveFieldByFieldElementComparatorOnFields(fields)
                .containsExactlyInAnyOrder(expectedAccount1, expectedAccount2, expectedAccount3);
    }

    @Test
    void refreshEntityStateStartWhenEmptyEntity() {
        // given
        long epochDay = 1000L;
        long timestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        domainBuilder.nodeStake().customize(n -> n.consensusTimestamp(timestamp).epochDay(epochDay)).persist();
        long balanceTimestamp = timestamp - 1000L;
        domainBuilder.accountBalanceFile().customize(abf -> abf.consensusTimestamp(balanceTimestamp)).persist();
        domainBuilder.accountBalance()
                .customize(ab -> ab.id(new AccountBalance.Id(balanceTimestamp, domainBuilder.entityId(ACCOUNT))))
                .persist();

        // when
        entityRepository.refreshEntityStateStart();

        // then
        assertThat(jdbcOperations.query("select * from entity_state_start", ROW_MAPPER)).isEmpty();
    }

    @Test
    void refreshEntityStateStartWhenEmptyAccountBalance() {
        // given
        long epochDay = 1000L;
        long timestamp = DomainUtils.convertToNanosMax(TestUtils.asStartOfEpochDay(epochDay + 1)) + 1000L;
        domainBuilder.nodeStake().customize(ns -> ns.consensusTimestamp(timestamp).epochDay(epochDay)).persist();
        domainBuilder.entity()
                .customize(e -> e.timestampRange(Range.atLeast(timestamp - 5000L)))
                .persist();

        // when
        entityRepository.refreshEntityStateStart();

        // then
        assertThat(jdbcOperations.query("select * from entity_state_start", ROW_MAPPER)).isEmpty();
    }

    @Test
    void refreshEntityStateStartWhenEmptyNodeStake() {
        // given
        long balanceTimestamp = 1_000_000_000L;
        var account = domainBuilder.entity()
                .customize(e -> e.timestampRange(Range.atLeast(balanceTimestamp - 1000L)))
                .persist();
        domainBuilder.accountBalanceFile().customize(abf -> abf.consensusTimestamp(balanceTimestamp)).persist();
        domainBuilder.accountBalance()
                .customize(ab -> ab.balance(100L)
                        .id(new AccountBalance.Id(balanceTimestamp, account.toEntityId())))
                .persist();

        // when
        entityRepository.refreshEntityStateStart();

        // then
        assertThat(jdbcOperations.query("select * from entity_state_start", ROW_MAPPER)).isEmpty();
    }

    @Test
    void updateContractType() {
        Entity entity = domainBuilder.entity().persist();
        Entity entity2 = domainBuilder.entity().persist();
        entityRepository.updateContractType(List.of(entity.getId(), entity2.getId()));
        assertThat(entityRepository.findAll())
                .hasSize(2)
                .extracting(Entity::getType)
                .allMatch(e -> e == CONTRACT);
    }
}
