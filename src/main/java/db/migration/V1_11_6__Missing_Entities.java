package db.migration;

import com.google.common.base.Stopwatch;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.recordFileLogger.Entities;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse.AccountInfo;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.*;
import java.nio.file.Paths;

@Log4j2
public class V1_11_6__Missing_Entities extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        File accountInfoFile = Paths.get(ConfigLoader.getDownloadToDir(), "accountInfo.txt").toFile();
        if (!accountInfoFile.exists() || !accountInfoFile.canRead()) {
            log.warn("Skipping entity import due to missing file {}", accountInfoFile.getAbsoluteFile());
            return;
        }

        Stopwatch stopwatch = Stopwatch.createStarted();
        long count = 0L;

        try (BufferedReader reader = new BufferedReader(new FileReader(accountInfoFile))) {
            String line = reader.readLine();
            Entities entities = new Entities(context.getConnection());

            while (line != null) {
                ++count;

                if (StringUtils.isNotBlank(line)) {
                    storeAccount(entities, line);
                }

                line = reader.readLine();
            }
        }

        log.info("Successfully loaded {} lines from {} in {}", count, accountInfoFile, stopwatch);
    }

    private void storeAccount(Entities entities, String line) {
        try {
            byte[] data = Base64.decodeBase64(line);
            AccountInfo accountInfo = AccountInfo.parseFrom(data);
            long expirationSec = 0L;
            long expirationNanos = 0L;
            if (accountInfo.hasExpirationTime()) {
                expirationSec = accountInfo.getExpirationTime().getSeconds();
                expirationNanos = accountInfo.getExpirationTime().getNanos();
            }

            long autoRenewPeriod = 0L;
            if (accountInfo.hasAutoRenewPeriod()) {
                autoRenewPeriod = accountInfo.getAutoRenewPeriod().getSeconds();
            }

            byte[] key = null;
            if (accountInfo.hasKey()) {
                key = accountInfo.getKey().toByteArray();
            }

            long proxyAccountId = 0L;
            if (accountInfo.hasProxyAccountID()) {
                proxyAccountId = entities.createOrGetEntity(accountInfo.getProxyAccountID());
            }

            long entityId = entities.createEntity(accountInfo.getAccountID(), expirationSec, expirationNanos, autoRenewPeriod, key, proxyAccountId);
            log.trace("Created account entity {}", entityId);

            if (accountInfo.getDeleted()) {
                entities.deleteEntity(accountInfo.getAccountID());
                log.warn("Deleting account: {}", accountInfo.getAccountID().getAccountNum());
            }
        } catch (Exception e) {
            log.error("Unable to load AccountInfo: {}", line, e);
        }
    }
}
