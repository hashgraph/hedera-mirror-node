package com.hedera.mirror.migration;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import com.google.common.base.Stopwatch;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Named;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.MirrorProperties;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.EntityType;
import com.hedera.mirror.repository.EntityRepository;
import com.hedera.mirror.repository.EntityTypeRepository;
import com.hedera.mirror.util.Utility;

@Log4j2
@Named
public class V1_11_6__Missing_Entities extends BaseJavaMigration {

    private final MirrorProperties mirrorProperties;
    private final EntityRepository entityRepository;
    private final EntityTypeRepository entityTypeRepository;
    private final JdbcOperations jdbcOperations;

    // There's a circular dependency of Flyway -> this -> Repositories/JdbcOperations -> Flyway, so use @Lazy to break it.
    // Correct way is to not use repositories and construct manually: new JdbcTemplate(context.getConnection())
    public V1_11_6__Missing_Entities(MirrorProperties mirrorProperties, @Lazy EntityRepository entityRepository,
            @Lazy EntityTypeRepository entityTypeRepository, @Lazy JdbcOperations jdbcOperations) {
        this.mirrorProperties = mirrorProperties;
        this.entityRepository = entityRepository;
        this.entityTypeRepository = entityTypeRepository;
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public void migrate(Context context) throws Exception {
        File accountInfoFile = getAccountInfoPath().toFile();
        if (!accountInfoFile.exists() || !accountInfoFile.canRead()) {
            log.warn("Skipping entity import due to missing file {}", accountInfoFile.getAbsoluteFile());
            return;
        }

        log.info("Importing account file {}", accountInfoFile.getAbsoluteFile());
        Stopwatch stopwatch = Stopwatch.createStarted();
        long count = 0L;

        try (BufferedReader reader = new BufferedReader(new FileReader(accountInfoFile))) {
            String line = null;

            while ((line = reader.readLine()) != null) {
                try {
                    if (StringUtils.isNotBlank(line)) {
                        updateAccount(line);
                    }

                    ++count;
                } catch (Exception e) {
                    log.error("Unable to load AccountInfo: {}", line, e);
                }
            }
        }

        log.info("Successfully loaded {} accounts in {}", count, stopwatch);
    }

    Path getAccountInfoPath() {
        return mirrorProperties.getDataPath().resolve("accountInfo.txt");
    }

    private void updateAccount(String line) throws Exception {
        byte[] data = Base64.decodeBase64(line);
        AccountInfo accountInfo = AccountInfo.parseFrom(data);
        AccountID accountID = accountInfo.getAccountID();

        Optional<Entities> entityExists = entityRepository
                .findByPrimaryKey(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum());
        if (entityExists.isPresent() && hasCreateTransaction(entityExists.get())) {
            return;
        }

        Entities entity = entityExists.orElseGet(() -> toEntity(accountID));

        if (entity.getExpiryTimeNs() == null && accountInfo.hasExpirationTime()) {
            try {
                entity.setExpiryTimeNs(Utility.timeStampInNanos(accountInfo.getExpirationTime()));
            } catch (ArithmeticException e) {
                log.warn("Invalid expiration time for account {}: {}", accountID.getAccountNum(), StringUtils.trim(e.getMessage()));
            }
        }

        if (entity.getAutoRenewPeriod() == null && accountInfo.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(accountInfo.getAutoRenewPeriod().getSeconds());
        }

        if (entity.getKey() == null && accountInfo.hasKey()) {
            entity.setKey(accountInfo.getKey().toByteArray());
        }

        if (entity.getProxyAccountId() == null && accountInfo.hasProxyAccountID()) {
            AccountID proxyAccountID = accountInfo.getProxyAccountID();
            Entities proxyEntity = entityRepository
                    .findByPrimaryKey(proxyAccountID.getShardNum(), proxyAccountID.getRealmNum(), proxyAccountID
                            .getAccountNum())
                    .orElseGet(() -> entityRepository.save(toEntity(proxyAccountID)));
            entity.setProxyAccountId(proxyEntity.getId());
        }

        if (accountInfo.getDeleted()) {
            entity.setDeleted(accountInfo.getDeleted());
        }

        if (entityExists.isPresent()) {
            log.debug("Updating entity {} for account {}", entity.getId(), accountID);
        } else {
            log.debug("Creating entity for account {}", accountID);
        }

        entityRepository.save(entity);
    }

    private Entities toEntity(AccountID accountID) {
        Entities entity = new Entities();
        entity.setEntityNum(accountID.getAccountNum());
        entity.setEntityRealm(accountID.getRealmNum());
        entity.setEntityShard(accountID.getShardNum());
        entity.setEntityTypeId(entityTypeRepository.findByName("account").map(EntityType::getId).get());
        return entity;
    }

    private boolean hasCreateTransaction(Entities entity) {
        return jdbcOperations.queryForObject("select count(*) > 0 from t_transactions t, t_transaction_types tt where " +
                        "t.fk_cud_entity_id = ? and t.fk_trans_type_id = tt.id and tt.name in (?,?,?)",
                new Object[] {entity.getId(), "CONTRACTCREATEINSTANCE", "CRYPTOCREATEACCOUNT", "FILECREATE"}, Boolean.class);
    }
}
