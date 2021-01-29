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

import com.google.common.base.Stopwatch;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Named;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcOperations;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.util.Utility;

@Named
public class V1_11_6__Missing_Entities extends MirrorBaseJavaMigration {

    private final MirrorProperties mirrorProperties;
    private final EntityRepository entityRepository;
    private final JdbcOperations jdbcOperations;

    // There's a circular dependency of Flyway -> this -> Repositories/JdbcOperations -> Flyway, so use @Lazy to
    // break it.
    // Correct way is to not use repositories and construct manually: new JdbcTemplate(context.getConnection())
    public V1_11_6__Missing_Entities(MirrorProperties mirrorProperties, @Lazy EntityRepository entityRepository,
                                     @Lazy JdbcOperations jdbcOperations) {
        this.mirrorProperties = mirrorProperties;
        this.entityRepository = entityRepository;
        this.jdbcOperations = jdbcOperations;
    }

    @Override
    public void doMigrate() throws IOException {
        File accountInfoFile = getAccountInfoPath().toFile();
        if (!accountInfoFile.exists() || !accountInfoFile.canRead()) {
            log.warn("Skipping entity import due to missing file {}", accountInfoFile.getAbsoluteFile());
            return;
        }

        log.info("Importing account file {}", accountInfoFile.getAbsoluteFile());
        Stopwatch stopwatch = Stopwatch.createStarted();
        long count = 0L;

        try (BufferedReader reader = new BufferedReader(new FileReader(accountInfoFile))) {
            String line;

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
        EntityId accountEntityId = EntityId.of(accountInfo.getAccountID());

        Optional<Entities> entityExists = entityRepository.findById(accountEntityId.getId());
        if (entityExists.isPresent() && hasCreateTransaction(entityExists.get())) {
            return;
        }

        Entities entity = entityExists.orElseGet(() -> accountEntityId.toEntity());

        if (entity.getExpiryTimeNs() == null && accountInfo.hasExpirationTime()) {
            try {
                entity.setExpiryTimeNs(Utility.timeStampInNanos(accountInfo.getExpirationTime()));
            } catch (ArithmeticException e) {
                log.warn("Invalid expiration time for account {}: {}", entity.getEntityNum(),
                        StringUtils.trim(e.getMessage()));
            }
        }

        if (entity.getAutoRenewPeriod() == null && accountInfo.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(accountInfo.getAutoRenewPeriod().getSeconds());
        }

        if (entity.getKey() == null && accountInfo.hasKey()) {
            entity.setKey(accountInfo.getKey().toByteArray());
        }

        if (entity.getProxyAccountId() == null && accountInfo.hasProxyAccountID()) {
            EntityId proxyAccountEntityId = EntityId.of(accountInfo.getProxyAccountID());
            // Persist if doesn't exist
            if (entityRepository.findById(proxyAccountEntityId.getId()).isEmpty()) {
                entityRepository.save(proxyAccountEntityId.toEntity());
            }
            entity.setProxyAccountId(proxyAccountEntityId);
        }

        if (accountInfo.getDeleted()) {
            entity.setDeleted(accountInfo.getDeleted());
        }

        if (entityExists.isPresent()) {
            log.debug("Updating entity {} for account {}", entity.getEntityNum(), accountEntityId);
        } else {
            log.debug("Creating entity for account {}", accountEntityId);
        }

        entityRepository.save(entity);
    }

    private boolean hasCreateTransaction(Entities entity) {
        return jdbcOperations
                .queryForObject("select count(*) > 0 from t_transactions t, t_transaction_types tt where " +
                                "t.entity_realm = ? and t.entity_num = ? and t.fk_trans_type_id = tt.id " +
                                "and tt.name in (?,?,?)",
                        new Object[] {entity.getEntityNum(), entity.getEntityRealm(), "CONTRACTCREATEINSTANCE",
                                "CRYPTOCREATEACCOUNT", "FILECREATE"}, Boolean.class);
    }
}
