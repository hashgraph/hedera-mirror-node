package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;
import javax.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;

class HistoricalAccountInfoMigrationTest extends IntegrationTest {

    // These are the three accounts present in the test accountInfo.txt.gz
    private static final long ACCOUNT_ID1 = 2977L;
    private static final long ACCOUNT_ID2 = 2978L;
    private static final long ACCOUNT_ID3 = 2979L;
    private static final int COUNT = 3;

    @Resource
    private HistoricalAccountInfoMigration historicalAccountInfoMigration;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private MirrorProperties mirrorProperties;

    private MirrorProperties.HederaNetwork network;

    @BeforeEach
    void before() {
        network = mirrorProperties.getNetwork();
        mirrorProperties.setImportHistoricalAccountInfo(true);
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.MAINNET);
        mirrorProperties.setStartDate(Instant.EPOCH);
    }

    @AfterEach
    void after() {
        mirrorProperties.setImportHistoricalAccountInfo(false);
        mirrorProperties.setNetwork(network);
        mirrorProperties.setStartDate(null);
    }

    @Test
    void noExistingEntities() throws Exception {
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(COUNT);
    }

    @Test
    void existingEntitiesFromBeforeReset() throws Exception {
        Entities entity1 = createEntity(ACCOUNT_ID1, EntityTypeEnum.ACCOUNT, false);
        Entities entity2 = createEntity(ACCOUNT_ID2, EntityTypeEnum.ACCOUNT, false);
        Entities entity3 = createEntity(ACCOUNT_ID3, EntityTypeEnum.ACCOUNT, false);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.findAll())
                .hasSize(COUNT)
                .allMatch(e -> e.getAutoRenewPeriod() > 0)
                .allMatch(e -> e.getExpiryTimeNs() > 0)
                .allMatch(e -> e.getKey().length > 0)
                .map(Entities::getEntityNum)
                .containsExactly(entity1.getEntityNum(), entity2.getEntityNum(), entity3.getEntityNum());
    }

    @Test
    void existingEntitiesAfterReset() throws Exception {
        Entities entity1 = createEntity(ACCOUNT_ID1, EntityTypeEnum.ACCOUNT, true);
        Entities entity2 = createEntity(ACCOUNT_ID2, EntityTypeEnum.CONTRACT, true);
        Entities entity3 = createEntity(ACCOUNT_ID3, EntityTypeEnum.CONTRACT, true);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2, entity3); // No update
    }

    @Test
    void noChangesWhenRanAgain() throws Exception {
        historicalAccountInfoMigration.doMigrate();
        Iterable<Entities> entities = entityRepository.findAll();
        assertThat(entities).hasSize(COUNT);

        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.findAll()).containsAll(entities);
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
        mirrorProperties.setStartDate(historicalAccountInfoMigration.EXPORT_DATE.minusNanos(1));
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(COUNT);
    }

    @Test
    void startDateEquals() throws Exception {
        mirrorProperties.setStartDate(historicalAccountInfoMigration.EXPORT_DATE);
        historicalAccountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(COUNT);
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
        String publicKey = Utility.convertSimpleKeyToHex(accountInfo.getKey().toByteArray());

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entities::getAutoRenewPeriod))
                .returns(accountInfo.getDeleted(), from(Entities::isDeleted))
                .returns(publicKey, from(Entities::getEd25519PublicKeyHex))
                .returns(Utility.timeStampInNanos(accountInfo.getExpirationTime()), from(Entities::getExpiryTimeNs))
                .returns(accountInfo.getKey().toByteArray(), from(Entities::getKey))
                .returns(accountInfo.getMemo(), from(Entities::getMemo))
                .returns(EntityId.of(accountInfo.getProxyAccountID()), from(Entities::getProxyAccountId));
    }

    @Test
    void update() {
        createEntity(ACCOUNT_ID1, EntityTypeEnum.ACCOUNT, false);
        AccountInfo.Builder accountInfo = accountInfo();
        String publicKey = Utility.convertSimpleKeyToHex(accountInfo.getKey().toByteArray());

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entities::getAutoRenewPeriod))
                .returns(accountInfo.getDeleted(), from(Entities::isDeleted))
                .returns(publicKey, from(Entities::getEd25519PublicKeyHex))
                .returns(Utility.timeStampInNanos(accountInfo.getExpirationTime()), from(Entities::getExpiryTimeNs))
                .returns(accountInfo.getKey().toByteArray(), from(Entities::getKey))
                .returns(accountInfo.getMemo(), from(Entities::getMemo))
                .returns(EntityId.of(accountInfo.getProxyAccountID()), from(Entities::getProxyAccountId));
    }

    @Test
    void emptyValues() {
        AccountID accountId = AccountID.newBuilder().setAccountNum(ACCOUNT_ID1).build();
        AccountInfo.Builder accountInfo = accountInfo().clear().setAccountID(accountId);
        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .returns(null, from(Entities::getAutoRenewPeriod))
                .returns(false, from(Entities::isDeleted))
                .returns(null, from(Entities::getEd25519PublicKeyHex))
                .returns(null, from(Entities::getExpiryTimeNs))
                .returns(null, from(Entities::getKey))
                .returns("", from(Entities::getMemo))
                .returns(null, from(Entities::getProxyAccountId));
    }

    @Test
    void deleted() {
        AccountInfo.Builder accountInfo = accountInfo().setDeleted(true);

        assertThat(historicalAccountInfoMigration.process(accountInfo.build())).isTrue();

        assertThat(entityRepository.findById(ACCOUNT_ID1))
                .get()
                .extracting(Entities::isDeleted)
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
                .extracting(Entities::getExpiryTimeNs)
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void skipExisting() {
        AccountInfo.Builder accountInfo = accountInfo();
        Entities entity = createEntity(ACCOUNT_ID1, EntityTypeEnum.ACCOUNT, true);
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

    private Entities createEntity(long num, EntityTypeEnum type, boolean afterReset) {
        Entities entities = new Entities();
        entities.setEntityNum(num);
        entities.setEntityRealm(0L);
        entities.setEntityShard(0L);
        entities.setEntityTypeId(type.getId());
        entities.setId(num);

        if (afterReset) {
            Key key = Key.newBuilder().setEd25519(ByteString.copyFromUtf8("123")).build();
            entities.setAutoRenewPeriod(5L);
            entities.setExpiryTimeNs(1L);
            entities.setKey(key.toByteArray());
            entities.setMemo("Bar");
            entities.setProxyAccountId(EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT));
        }

        entityRepository.save(entities);
        return entities;
    }
}
