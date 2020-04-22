package com.hedera.mirror.importer.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import javax.annotation.Resource;
import javax.sql.DataSource;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.assertj.core.api.Assertions;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityType;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EntityTypeRepository;
import com.hedera.mirror.importer.util.Utility;

@Disabled("This refreshes the ApplicationContext halfway through tests, causing multiple DataSource objects to be in " +
        "use due to the DatabaseUtilities hack. Can be re-enabled when DatabaseUtilities is deleted")
@TestPropertySource(properties = "spring.flyway.target=1.11.5")
public class V1_11_6__Missing_EntitiesTest extends IntegrationTest {

    @TempDir
    Path tempDir;
    private final AccountID accountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(1).build();
    @Resource
    private V1_11_6__Missing_Entities migration;
    @Resource
    private DataSource dataSource;
    @Resource
    private EntityRepository entityRepository;
    @Resource
    private EntityTypeRepository entityTypeRepository;
    @Resource
    private MirrorProperties mirrorProperties;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void before() {
        mirrorProperties.setDataPath(tempDir);
    }

    @Test
    void create() throws Exception {
        AccountInfo.Builder accountInfo = accountInfo();
        write(accountInfo.build());
        migration.migrate(new FlywayContext());
        Assertions.assertThat(entityRepository
                .findByPrimaryKey(accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum()))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entities::getAutoRenewPeriod))
                .returns(Utility.protobufKeyToHexIfEd25519OrNull(accountInfo.getKey()
                        .toByteArray()), from(Entities::getEd25519PublicKeyHex))
                .returns(Utility.timeStampInNanos(accountInfo.getExpirationTime()), from(Entities::getExpiryTimeNs))
                .returns(accountInfo.getKey().toByteArray(), from(Entities::getKey));
    }

    @Test
    void nullValues() throws Exception {
        write(AccountInfo.newBuilder().setAccountID(accountId).build());
        migration.migrate(new FlywayContext());
        Assertions.assertThat(entityRepository
                .findByPrimaryKey(accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum()))
                .get()
                .returns(null, from(Entities::getAutoRenewPeriod))
                .returns(null, from(Entities::getEd25519PublicKeyHex))
                .returns(null, from(Entities::getExpiryTimeNs))
                .returns(null, from(Entities::getKey));
    }

    @Test
    void noUpdateIfCreateTransaction() throws Exception {
        AccountInfo.Builder accountInfo = accountInfo();
        write(accountInfo.build());

        Entities entity = entity(accountId.getAccountNum());
        Entities node = entity(2);
        Entities payer = entity(3);

        long transactionTypeId = jdbcTemplate
                .queryForObject("select id from t_transaction_types where name = ?", new Object[] {
                        "CRYPTOCREATEACCOUNT"}, Integer.class);

        jdbcTemplate.update("insert into t_transactions (consensus_ns, fk_cud_entity_id, fk_node_acc_id, " +
                        "fk_payer_acc_id, fk_trans_type_id, valid_start_ns) values(?,?,?,?,?,?,?)",
                1L, entity.getId(), node.getId(), payer.getId(), transactionTypeId, 1L);

        migration.migrate(new FlywayContext());

        Assertions.assertThat(entityRepository.findAll())
                .extracting(Entities::getId)
                .containsExactlyInAnyOrder(entity.getId(), node.getId(), payer.getId());

        Assertions.assertThat(entityRepository.findById(entity.getId()))
                .get()
                .extracting(Entities::getKey)
                .withFailMessage("Expecting entity <%d> to not be updated", entity.getId())
                .isNull();
    }

    @Test
    void updateIfNoCreateTransaction() throws Exception {
        Entities entity = entity(accountId.getAccountNum());
        AccountInfo.Builder accountInfo = accountInfo();
        write(accountInfo.build());
        migration.migrate(new FlywayContext());

        Assertions.assertThat(entityRepository.findById(entity.getId()))
                .get()
                .returns(accountInfo.getAutoRenewPeriod().getSeconds(), from(Entities::getAutoRenewPeriod))
                .returns(Utility.protobufKeyToHexIfEd25519OrNull(accountInfo.getKey()
                        .toByteArray()), from(Entities::getEd25519PublicKeyHex))
                .returns(Utility.timeStampInNanos(accountInfo.getExpirationTime()), from(Entities::getExpiryTimeNs))
                .returns(accountInfo.getKey().toByteArray(), from(Entities::getKey));
    }

    @Test
    void deleted() throws Exception {
        AccountInfo.Builder accountInfo = accountInfo();
        accountInfo.setDeleted(true);
        write(accountInfo.build());

        migration.migrate(new FlywayContext());

        Assertions.assertThat(entityRepository
                .findByPrimaryKey(accountId.getShardNum(), accountId.getRealmNum(), accountId.getAccountNum()))
                .get()
                .extracting(Entities::isDeleted)
                .isEqualTo(true);
    }

    @Test
    void emptyFile() throws Exception {
        Files.write(migration.getAccountInfoPath(), new byte[] {});
        migration.migrate(new FlywayContext());
        assertThat(entityRepository.count()).isEqualTo(0L);
    }

    @Test
    void missingFile() throws Exception {
        migration.migrate(new FlywayContext());
        assertThat(entityRepository.count()).isEqualTo(0L);
    }

    @Test
    void corruptFile() throws Exception {
        AccountInfo.Builder accountInfo = accountInfo();
        Files.write(migration.getAccountInfoPath(), new Hex().encode(accountInfo.build().toByteArray()));
        migration.migrate(new FlywayContext());
        assertThat(entityRepository.count()).isEqualTo(0L);
    }

    @Test
    void longOverflow() throws Exception {
        AccountInfo.Builder accountInfo = accountInfo()
                .setExpirationTime(Timestamp.newBuilder().setSeconds(31556889864403199L).build());
        write(accountInfo.build());
        migration.migrate(new FlywayContext());
        Assertions.assertThat(entityRepository.findAll())
                .hasSize(2)
                .extracting(Entities::getExpiryTimeNs)
                .containsOnlyNulls();
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
        Entities entity = new Entities();
        entity.setEntityNum(num);
        entity.setEntityRealm(0L);
        entity.setEntityShard(0L);
        entity.setEntityTypeId(entityTypeRepository.findByName("account").map(EntityType::getId).orElse(null));
        return entityRepository.save(entity);
    }

    void write(AccountInfo accountInfo) throws Exception {
        byte[] line = Base64.encodeBase64(accountInfo.toByteArray());
        Files.write(migration.getAccountInfoPath(), line);
    }

    private class FlywayContext implements Context {

        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public Connection getConnection() {
            try {
                return dataSource.getConnection();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
