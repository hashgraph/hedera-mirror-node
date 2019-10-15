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
import com.hedera.configLoader.ConfigLoader;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.EntityType;
import com.hedera.mirror.repository.*;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import javax.inject.Named;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Log4j2
@Named
@RequiredArgsConstructor
public class V1_11_6__Missing_Entities extends BaseJavaMigration {

    private final EntityRepository entityRepository;
    private final EntityTypeRepository entityTypeRepository;
    private final TransactionRepository transactionRepository;

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
        return Paths.get(ConfigLoader.getDownloadToDir(), "accountInfo.txt");
    }

    private void updateAccount(String line) throws Exception {
        byte[] data = Base64.decodeBase64(line);
        AccountInfo accountInfo = AccountInfo.parseFrom(data);
        AccountID accountID = accountInfo.getAccountID();

        Optional<Entities> entityExists = entityRepository.findByPrimaryKey(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum());
        if (entityExists.isPresent() && hasCreateTransaction(entityExists.get())) {
            return;
        }

        Entities entity = entityExists.orElseGet(() -> toEntity(accountID));

        if (entity.getExpiryTimeNs() == null && accountInfo.hasExpirationTime()) {
            entity.setExpiryTimeNs(Utility.timeStampInNanos(accountInfo.getExpirationTime()));
        }

        if (entity.getAutoRenewPeriod() == null && accountInfo.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(accountInfo.getAutoRenewPeriod().getSeconds());
        }

        if (entity.getKey() == null && accountInfo.hasKey()) {
            entity.setKey(accountInfo.getKey().toByteArray());
        }

        if (entity.getProxyAccountId() == null && accountInfo.hasProxyAccountID()) {
            AccountID proxyAccountID = accountInfo.getProxyAccountID();
            Entities proxyEntity = entityRepository.findByPrimaryKey(proxyAccountID.getShardNum(), proxyAccountID.getRealmNum(), proxyAccountID.getAccountNum())
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
        return transactionRepository.existsByEntityAndType(entity.getId(), "CONTRACTCREATEINSTANCE", "CRYPTOCREATEACCOUNT", "FILECREATE");
    }
}
