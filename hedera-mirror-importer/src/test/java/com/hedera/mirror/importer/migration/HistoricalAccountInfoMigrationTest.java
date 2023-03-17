package com.hedera.mirror.importer.migration;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

import com.google.common.collect.Range;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;

@RequiredArgsConstructor(onConstructor = @__(@Autowired))
class HistoricalAccountInfoMigrationTest extends IntegrationTest {

    // These are the three accounts present in the test accountInfo.txt.gz
    private static final long ACCOUNT_ID1 = 2977L;
    private static final long ACCOUNT_ID2 = 2978L;
    private static final long ACCOUNT_ID3 = 2979L;
    private static final long CONTRACT_ID1 = 13236L;
    private static final int CONTRACT_COUNT = 1;
    private static final int ENTITY_COUNT = 3;

    private final HistoricalAccountInfoMigration historicalAccountInfoMigration;
    private final EntityRepository entityRepository;
    private final MirrorProperties mirrorProperties;

    private MirrorProperties.HederaNetwork network;

    @BeforeEach
    void before() {
        network = mirrorProperties.getNetwork();
        mirrorProperties.setImportHistoricalAccountInfo(true);
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.MAINNET);
    }

    @AfterEach
    void after() {
        mirrorProperties.setImportHistoricalAccountInfo(false);
        mirrorProperties.setNetwork(network);
    }

    @Test
    void checksum() {
        assertThat(historicalAccountInfoMigration.getChecksum()).isEqualTo(3);
    }

    @Test
    void noExistingEntities() throws Exception {
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(ENTITY_COUNT + CONTRACT_COUNT);
    }

    @Test
    void existingEntitiesFromBeforeReset() throws Exception {
        Entity entity1 = createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, false);
        Entity entity2 = createEntity(ACCOUNT_ID2, EntityType.ACCOUNT, false);
        Entity entity3 = createEntity(ACCOUNT_ID3, EntityType.ACCOUNT, false);
        Entity contract1 = createEntity(CONTRACT_ID1, EntityType.CONTRACT, false);

        historicalAccountInfoMigration.doMigrate();

        assertThat(entityRepository.findAll())
                .hasSize(ENTITY_COUNT + CONTRACT_COUNT)
                .allMatch(e -> e.getAutoRenewPeriod() > 0)
                .allMatch(e -> e.getExpirationTimestamp() > 0)
                .allMatch(e -> e.getKey().length > 0)
                .map(Entity::getNum)
                .containsExactlyInAnyOrder(entity1.getNum(), entity2.getNum(), entity3.getNum(), contract1.getNum());
    }

    @Test
    void existingEntitiesAfterReset() throws Exception {
        Entity entity1 = createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, true);
        Entity entity2 = createEntity(ACCOUNT_ID2, EntityType.ACCOUNT, true);
        Entity entity3 = createEntity(ACCOUNT_ID3, EntityType.ACCOUNT, true);
        Entity contract1 = createEntity(CONTRACT_ID1, EntityType.CONTRACT, true);

        historicalAccountInfoMigration.doMigrate();

        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2, entity3, contract1);
    }

    @Test
    void existingEntitiesWrongType() throws Exception {
        Entity entity1 = createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, true);
        Entity entity2 = createEntity(ACCOUNT_ID2, EntityType.ACCOUNT, true);
        Entity entity3 = createEntity(ACCOUNT_ID3, EntityType.ACCOUNT, true);
        Entity entity4 = createEntity(CONTRACT_ID1, EntityType.ACCOUNT, true);

        historicalAccountInfoMigration.doMigrate();

        entity4.setType(EntityType.CONTRACT);
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2, entity3, entity4);
    }

    @Test
    void noChangesWhenRanAgain() throws Exception {
        historicalAccountInfoMigration.doMigrate();

        Iterable<Entity> entities = entityRepository.findAll();
        assertThat(entities).hasSize(ENTITY_COUNT + CONTRACT_COUNT);

        historicalAccountInfoMigration.doMigrate();

        assertThat(entityRepository.findAll()).containsExactlyInAnyOrderElementsOf(entities);
    }

    @Test
    void disabled() throws Exception {
        mirrorProperties.setImportHistoricalAccountInfo(false);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void notMainnet() throws Exception {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.DEMO);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void startDateAfter() throws Exception {
        mirrorProperties.setStartDate(Instant.now());
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void startDateBefore() throws Exception {
        mirrorProperties.setStartDate(HistoricalAccountInfoMigration.EXPORT_DATE.minusNanos(1));
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(ENTITY_COUNT + CONTRACT_COUNT);
    }

    @Test
    void startDateEquals() throws Exception {
        mirrorProperties.setStartDate(HistoricalAccountInfoMigration.EXPORT_DATE);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(ENTITY_COUNT + CONTRACT_COUNT);
    }

    @Test
    void startDateNull() throws Exception {
        mirrorProperties.setStartDate(null);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isZero();
    }

    @Test
    void create() {
        AccountInfo.Builder accountInfo = accountInfo();
        String publicKey = DomainUtils.getPublicKey(accountInfo.getKey().toByteArray());

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entity::getAutoRenewPeriod))
                .returns(accountInfo.getDeleted(), from(Entity::getDeleted))
                .returns(publicKey, from(Entity::getPublicKey))
                .returns(DomainUtils
                        .timeStampInNanos(accountInfo.getExpirationTime()), from(Entity::getExpirationTimestamp))
                .returns(accountInfo.getKey().toByteArray(), from(Entity::getKey))
                .returns(accountInfo.getMemo(), from(Entity::getMemo))
                .returns(EntityId.of(accountInfo.getProxyAccountID()), from(Entity::getProxyAccountId));
    }

    @Test
    void update() {
        createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, false);
        AccountInfo.Builder accountInfo = accountInfo();
        String publicKey = DomainUtils.getPublicKey(accountInfo.getKey().toByteArray());

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entity::getAutoRenewPeriod))
                .returns(accountInfo.getDeleted(), from(Entity::getDeleted))
                .returns(publicKey, from(Entity::getPublicKey))
                .returns(DomainUtils
                        .timeStampInNanos(accountInfo.getExpirationTime()), from(Entity::getExpirationTimestamp))
                .returns(accountInfo.getKey().toByteArray(), from(Entity::getKey))
                .returns(accountInfo.getMemo(), from(Entity::getMemo))
                .returns(EntityId.of(accountInfo.getProxyAccountID()), from(Entity::getProxyAccountId));
    }

    @Test
    void emptyValues() {
        AccountID accountId = AccountID.newBuilder().setAccountNum(ACCOUNT_ID1).build();
        AccountInfo.Builder accountInfo = accountInfo().clear().setAccountID(accountId);
        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(null, from(Entity::getAutoRenewPeriod))
                .returns(false, from(Entity::getDeleted))
                .returns(null, from(Entity::getPublicKey))
                .returns(null, from(Entity::getExpirationTimestamp))
                .returns(null, from(Entity::getKey))
                .returns("", from(Entity::getMemo))
                .returns(null, from(Entity::getProxyAccountId));
    }

    @Test
    void deleted() {
        AccountInfo.Builder accountInfo = accountInfo().setDeleted(true);

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .extracting(Entity::getDeleted)
                .isEqualTo(true);
    }

    @Test
    void longOverflow() {
        AccountInfo.Builder accountInfo = accountInfo()
                .setExpirationTime(Timestamp.newBuilder().setSeconds(31556889864403199L).build());
        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();
        assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .extracting(Entity::getExpirationTimestamp)
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void skipExisting() {
        AccountInfo.Builder accountInfo = accountInfo();
        Entity entity = createEntity(ACCOUNT_ID1, EntityType.ACCOUNT, true);
        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isFalse();
        assertThat(entityRepository.findAll()).hasSize(1).containsExactly(entity);
    }

    @Test
    void skipMigrationFalse() {
        assertThat(historicalAccountInfoMigration.skipMigration(null)).isFalse();
    }

    private AccountInfo.Builder accountInfo() {
        return AccountInfo.newBuilder()
                .setAccountID(AccountID.newBuilder().setAccountNum(ACCOUNT_ID1).build())
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(1).build())
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8("abc")).build())
                .setMemo("Foo")
                .setProxyAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2).build());
    }

    private Entity createEntity(long num, EntityType type, boolean afterReset) {
        Entity entity = EntityId.of(0L, 0L, num, type).toEntity();
        entity.setDeclineReward(false);
        entity.setNum(num);
        entity.setRealm(0L);
        entity.setShard(0L);
        entity.setType(type);
        entity.setId(num);
        entity.setDeleted(false);
        entity.setMemo("");
        entity.setTimestampRange(Range.atLeast(0L));

        if (afterReset) {
            Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8("123")).build();
            entity.setAutoRenewPeriod(5L);
            entity.setExpirationTimestamp(1L);
            entity.setKey(key.toByteArray());
            entity.setMemo("Bar");
            entity.setProxyAccountId(EntityId.of(0, 0, 3, EntityType.ACCOUNT));
        }

        entityRepository.save(entity);
        return entity;
    }
}
