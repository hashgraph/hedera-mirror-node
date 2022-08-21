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

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.common.domain.entity.Entity;

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
    void updateContractType() {
        Entity entity = domainBuilder.entity().persist();
        Entity entity2 = domainBuilder.entity().persist();
        entityRepository.updateContractType(List.of(entity.getId(), entity2.getId()));
        assertThat(entityRepository.findAll())
                .hasSize(2)
                .extracting(Entity::getType)
                .allMatch(e -> e == CONTRACT);
    }

//    @ParameterizedTest
//    @CsvSource({
//            "0, false, 1, 15090000000, ACCOUNT, 1500",
//            "20, false, 1, 15090000000, ACCOUNT, 1520",
//            "20, false, 1, 15090000000, CONTRACT, 1520",
//            "0, false, 2, 15090000000, ACCOUNT, 0",
//            "0, false, -1, 15090000000, ACCOUNT, 0",
//            "0, false, , 15090000000, ACCOUNT, 0",
//            "0, true, 1, 15090000000, ACCOUNT, 0",
//            "0, false, -1, 0, TOPIC, 0",
//    })
//    void updatePendingReward(long currentPendingReward, boolean declineRewardPrevious, Long stakedNodeIdPrevious,
//                             long stakeTotalPrevious, EntityType type, long expectedPendingReward) {
//        // given
//        var entity = domainBuilder.entity()
//                .customize(e -> e.pendingReward(currentPendingReward)
//                        .declineRewardPrevious(declineRewardPrevious)
//                        .stakedNodeIdPrevious(stakedNodeIdPrevious)
//                        .stakeTotalPrevious(stakeTotalPrevious)
//                        .type(type))
//                .persist();
//
//        // node 1 has nonzero rewardRate
//        // node 2 has zero rewardRate
//        long timestamp = domainBuilder.timestamp();
//        domainBuilder.nodeStake()
//                .customize(ns -> ns.nodeId(1L).rewardRate(10L).consensusTimestamp(timestamp))
//                .persist();
//        domainBuilder.nodeStake()
//                .customize(ns -> ns.nodeId(2L).rewardRate(0L).consensusTimestamp(timestamp))
//                .persist();
//
//        // when
//        entityRepository.updatePendingReward(timestamp);
//
//        // then
//        assertThat(entityRepository.findById(entity.getId()))
//                .get()
//                .returns(expectedPendingReward, Entity::getPendingReward);
//    }

//    @Test
//    void updateStakingState() {
//        // given
//        var entity1 = domainBuilder.entity()
//                .customize(e -> e.balance(100L).stakedAccountId(null).stakedNodeId(1L))
//                .persist();
//        var entity2 = domainBuilder.entity()
//                .customize(e -> e.balance(200L).declineReward(true).stakedAccountId(entity1.getId()))
//                .persist();
//        var entity3 = domainBuilder.entity()
//                .customize(e -> e.balance(300L).stakedAccountId(entity1.getId()).stakedNodeId(null).type(CONTRACT))
//                .persist();
//        var entity4 = domainBuilder.entity()
//                // deleted is true and balance != 0 shouldn't happen, it's just used to test that deleted accounts
//                // are skipped for staking state update
//                .customize(e -> e.balance(400L).deleted(true).stakedAccountId(entity1.getId()))
//                .persist();
//        var entity5 = domainBuilder.entity()
//                .customize(e -> e.balance(500L).stakedAccountId(entity3.getId()))
//                .persist();
//        var entity6 = domainBuilder.entity().customize(e -> e.balance(600L)).persist();
//        var entity7 = domainBuilder.entity()
//                .customize(e -> e.balance(null).stakedAccountId(null).stakedNodeId(null).type(TOPIC))
//                .persist();
//        var entity8 = domainBuilder.entity()
//                .customize(e -> e.balance(800L).stakedAccountId(entity6.getId()))
//                .persist();
//        long entityId9 = entity8.getId() + 1;
//        long entityId10 = entityId9 + 1;
//        var entity9 = domainBuilder.entity()
//                .customize(e -> e.balance(900L).id(entityId9).num(entityId9).stakedAccountId(entityId10))
//                .persist();
//        var entity10 = domainBuilder.entity()
//                .customize(e -> e.balance(1000L).id(entityId10).num(entityId10).stakedAccountId(entityId9))
//                .persist();
//
//        // when
//        entityRepository.updateStakingState();
//
//        // then
//        // entity1 stakes to a node with two alive entities stake to it
//        entity1.setStakedNodeIdPrevious(1L);
//        entity1.setStakedToMe(500L);
//        entity1.setStakeTotalPrevious(600L);
//        // entity2 stakes to entity1, declines reward, has no entities stake to it
//        entity2.setDeclineRewardPrevious(true);
//        // entity3 stakedNodeId is null, stakes to entity1, has entity5 stake to it
//        entity3.setStakedNodeIdPrevious(null);
//        entity3.setStakedToMe(500L);
//        // entity6 doesn't stake to either an entity or a node. However, since entity8
//        // stakes to it, its stakedToMe is 800
//        entity6.setStakedToMe(800L);
//        // entity9 and entity10 stake to each other
//        entity9.setStakedToMe(1000L);
//        entity10.setStakedToMe(900L);
//        assertThat(entityRepository.findAll())
//                .usingRecursiveFieldByFieldElementComparatorOnFields("id", "declineRewardPrevious",
//                        "stakedNodeIdPrevious", "stakedToMe", "stakeTotalPrevious")
//                .containsExactlyInAnyOrder(entity1, entity2, entity3, entity4, entity5, entity6, entity7, entity8,
//                        entity9, entity10);
//    }
}
