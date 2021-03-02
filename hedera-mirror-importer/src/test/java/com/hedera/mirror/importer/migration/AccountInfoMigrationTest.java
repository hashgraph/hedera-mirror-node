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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.Utility;

public class AccountInfoMigrationTest extends IntegrationTest {

    private final AccountID accountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build();

    @Resource
    private AccountInfoMigration accountInfoMigration;

    @Resource
    private EntityRepository entityRepository;

    @Resource
    private MirrorProperties mirrorProperties;

    @Resource
    private TransactionRepository transactionRepository;

    private MirrorProperties.HederaNetwork network;

    @BeforeEach
    void before() {
        network = mirrorProperties.getNetwork();
        mirrorProperties.setImportAccountInfo(true);
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.TESTNET);
    }

    @AfterEach
    void after() {
        mirrorProperties.setImportAccountInfo(false);
        mirrorProperties.setNetwork(network);
    }

    @Test
    void noExistingEntities() throws Exception {
        accountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(3);
    }

    @Test
    void noCreateTransactions() throws Exception {
        Entities entity1 = entity(2977);
        Entities entity2 = entity(2978);
        Entities entity3 = entity(2979);
        accountInfoMigration.doMigrate();
        assertThat(entityRepository.findAll())
                .hasSize(3)
                .allMatch(e -> e.getKey().length > 0)
                .allMatch(e -> e.getAutoRenewPeriod() > 0)
                .allMatch(e -> e.getExpiryTimeNs() > 0)
                .map(Entities::getEntityNum)
                .containsExactly(entity1.getEntityNum(), entity2.getEntityNum(), entity3.getEntityNum());
    }

    @Test
    void hasCreateTransactions() throws Exception {
        Entities entity1 = entity(2977);
        Entities entity2 = entity(2978);
        Entities entity3 = entity(2979);
        transaction(entity1);
        transaction(entity2);
        transaction(entity3);
        entityRepository.findAll().forEach(t -> System.out.println(t));
        transactionRepository.findAll().forEach(t -> System.out.println(t));

        accountInfoMigration.doMigrate();
        assertThat(entityRepository.findAll()).containsExactlyInAnyOrder(entity1, entity2, entity3); // No update
    }

    @Test
    void noChanges() throws Exception {
        accountInfoMigration.doMigrate();
        Iterable<Entities> entities = entityRepository.findAll();
        assertThat(entities).hasSize(3);

        accountInfoMigration.doMigrate();
        assertThat(entityRepository.findAll()).containsAll(entities);
    }

    @Test
    void disabled() throws Exception {
        mirrorProperties.setImportAccountInfo(false);
        accountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(0L);
    }

    @Test
    void missingFile() throws Exception {
        mirrorProperties.setNetwork(MirrorProperties.HederaNetwork.DEMO);
        accountInfoMigration.doMigrate();
        assertThat(entityRepository.count()).isEqualTo(0L);
    }

    @Test
    void create() throws Exception {
        AccountInfo.Builder accountInfo = accountInfo();
        accountInfoMigration.process(accountInfo.build());

        Assertions.assertThat(entityRepository.findById(EntityId.of(accountId).getId()))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entities::getAutoRenewPeriod))
                .returns(Utility.protobufKeyToHexIfEd25519OrNull(accountInfo.getKey()
                        .toByteArray()), from(Entities::getEd25519PublicKeyHex))
                .returns(Utility.timeStampInNanos(accountInfo.getExpirationTime()), from(Entities::getExpiryTimeNs))
                .returns(accountInfo.getKey().toByteArray(), from(Entities::getKey));
    }

    @Test
    void emptyValues() {
        accountInfoMigration.process(AccountInfo.newBuilder().setAccountID(accountId).build());

        Assertions.assertThat(entityRepository.findById(EntityId.of(accountId).getId()))
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
    void deleted() throws Exception {
        AccountInfo.Builder accountInfo = accountInfo().setDeleted(true);

        accountInfoMigration.process(accountInfo.build());

        Assertions.assertThat(entityRepository.findById(EntityId.of(accountId).getId()))
                .get()
                .extracting(Entities::isDeleted)
                .isEqualTo(true);
    }

    @Test
    void longOverflow() throws Exception {
        AccountInfo.Builder accountInfo = accountInfo()
                .setExpirationTime(Timestamp.newBuilder().setSeconds(31556889864403199L).build());
        accountInfoMigration.process(accountInfo.build());
        Assertions.assertThat(entityRepository.findAll())
                .hasSize(1)
                .first()
                .extracting(Entities::getExpiryTimeNs)
                .isEqualTo(Long.MAX_VALUE);
    }

    private AccountInfo.Builder accountInfo() throws Exception {
        return AccountInfo.newBuilder()
                .setAccountID(accountId)
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(5).build())
                .setExpirationTime(Utility.instantToTimestamp(Instant.now()))
                .setKey(Key.newBuilder().setEd25519(ByteString.copyFrom("123", "UTF-8")).build())
                .setProxyAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2).build());
    }

    private Entities entity(long num) {
        return entityRepository.save(EntityId.of(0L, 0L, num, EntityTypeEnum.ACCOUNT).toEntity());
    }

    private Transaction transaction(Entities entity) {
        Transaction transaction = new Transaction();
        transaction.setConsensusNs(entity.getId());
        transaction.setEntityId(entity.toEntityId());
        transaction.setNodeAccountId(EntityId.of(0, 0, 3, EntityTypeEnum.ACCOUNT));
        transaction.setPayerAccountId(EntityId.of(0, 0, 2, EntityTypeEnum.ACCOUNT));
        transaction.setResult(1);
        transaction.setType(TransactionTypeEnum.CRYPTOCREATEACCOUNT.getProtoId());
        transaction.setValidStartNs(1L);
        return transactionRepository.save(transaction);
    }
}
