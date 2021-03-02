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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.Utility;

@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class AccountInfoMigration extends MirrorBaseJavaMigration {

    private static final Collection<Integer> TRANSACTION_TYPES = Set.of(
            TransactionTypeEnum.CONTRACTCREATEINSTANCE.getProtoId(),
            TransactionTypeEnum.CRYPTOCREATEACCOUNT.getProtoId(),
            TransactionTypeEnum.FILECREATE.getProtoId()
    );

    @Value("classpath:accountInfo")
    private Path accountInfoDirectory;

    private final EntityRepository entityRepository;
    private final MirrorProperties mirrorProperties;
    private final TransactionRepository transactionRepository;

    @Override
    public Integer getChecksum() {
        return 1; // Change this if this migration should be re-ran
    }

    @Override
    public String getDescription() {
        return "Backfill pre-OA account information";
    }

    @Override
    public MigrationVersion getVersion() {
        return null; // Repeatable migration
    }

    @Override
    protected void doMigrate() throws IOException {
        if (!mirrorProperties.isImportAccountInfo()) {
            log.info("Account info importing is disabled");
            return;
        }

        String network = mirrorProperties.getNetwork().name().toLowerCase(Locale.ROOT);
        String filename = network + ".txt.gz";
        Path accountInfoPath = accountInfoDirectory.resolve(filename);

        if (!Files.isReadable(accountInfoPath)) {
            log.info("Skipping import due to missing account info file for {}", network);
            return;
        }

        log.info("Importing account info for {}", network);
        Stopwatch stopwatch = Stopwatch.createStarted();

        try (
                InputStream inputStream = new GZIPInputStream(new FileInputStream(accountInfoPath.toFile()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
        ) {
            long count = reader.lines()
                    .map(this::parse)
                    .filter(Objects::nonNull)
                    .map(this::process)
                    .filter(Boolean::booleanValue)
                    .count();
            log.info("Successfully updated {} accounts in {}", count, stopwatch);
        }
    }

    private AccountInfo parse(String line) {
        try {
            if (StringUtils.isNotBlank(line)) {
                byte[] data = Base64.decodeBase64(line);
                return AccountInfo.parseFrom(data);
            }
        } catch (Exception e) {
            log.error("Unable to parse AccountInfo from line: {}", line, e);
        }

        return null;
    }

    boolean process(AccountInfo accountInfo) {
        EntityId accountEntityId = EntityId.of(accountInfo.getAccountID());
        Optional<Entities> entityExists = entityRepository.findById(accountEntityId.getId());
        boolean exists = entityExists.isPresent();

        if (exists && hasCreateTransaction(accountEntityId)) {
            log.trace("Skipping entity {} that was created after the stream reset",
                    () -> accountEntityId.entityIdToString());
            return false;
        }

        Entities entity = entityExists.orElseGet(accountEntityId::toEntity);
        boolean updated = !exists;

        if (entity.getAutoRenewPeriod() == null && accountInfo.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(accountInfo.getAutoRenewPeriod().getSeconds());
            updated = true;
        }

        // Accounts can't be undeleted
        if (entity.isDeleted() != accountInfo.getDeleted() && accountInfo.getDeleted()) {
            entity.setDeleted(accountInfo.getDeleted());
            updated = true;
        }

        if (entity.getExpiryTimeNs() == null && accountInfo.hasExpirationTime()) {
            entity.setExpiryTimeNs(Utility.timestampInNanosMax(accountInfo.getExpirationTime()));
            updated = true;
        }

        if (entity.getKey() == null && accountInfo.hasKey()) {
            entity.setKey(accountInfo.getKey().toByteArray());
            updated = true;
        }

        if (entity.getMemo() == null && accountInfo.getMemo() != null) {
            entity.setMemo(accountInfo.getMemo());
            updated = true;
        }

        if (entity.getProxyAccountId() == null && accountInfo.hasProxyAccountID()) {
            EntityId proxyAccountEntityId = EntityId.of(accountInfo.getProxyAccountID());
            entity.setProxyAccountId(proxyAccountEntityId); // Proxy account should get created separately
            updated = true;
        }

        if (updated) {
            log.info("Saving {} entity: {}", exists ? "existing" : "new", entity);
            entityRepository.save(entity);
        }

        return updated;
    }

    private boolean hasCreateTransaction(EntityId entityId) {
        return transactionRepository.existsByEntityIdAndTypeIn(entityId, TRANSACTION_TYPES);
    }
}
