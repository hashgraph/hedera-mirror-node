package com.hedera.mirror.importer.migration;

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

import static com.hedera.mirror.importer.reader.record.RecordFileReader.MAX_TRANSACTION_LENGTH;

import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import com.hedera.mirror.common.domain.balance.AccountBalanceFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.parser.balance.BalanceStreamFileListener;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.EntityRecordItemListener;
import com.hedera.mirror.importer.reader.ValidatedDataInputStream;

@Log4j2
@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class ErrataMigration extends MirrorBaseJavaMigration implements BalanceStreamFileListener {

    @Value("classpath:errata/mainnet/balance-offsets.txt")
    private final Resource balanceOffsets;
    private final EntityRecordItemListener entityRecordItemListener;
    private final NamedParameterJdbcOperations jdbcOperations;
    private final MirrorProperties mirrorProperties;
    private final RecordStreamFileListener recordStreamFileListener;
    private final Set<Long> timestamps = new HashSet<>();

    @Override
    public Integer getChecksum() {
        return 1; // Change this if this migration should be rerun
    }

    @Override
    public String getDescription() {
        return "Add errata information to the database to workaround older, incorrect data";
    }

    @Override
    public MigrationVersion getVersion() {
        return null; // Repeatable migration
    }

    @Override
    public void onStart() {
        // Not applicable
    }

    @Override
    public void onEnd(AccountBalanceFile accountBalanceFile) {
        if (isMainnet() && getTimestamps().contains(accountBalanceFile.getConsensusTimestamp())) {
            accountBalanceFile.setTimeOffset(-1);
        }
    }

    @Override
    public void onError() {
        // Not applicable
    }

    @Override
    protected void doMigrate() throws IOException {
        if (isMainnet()) {
            balanceFileAdjustment();
            missingTransactions();
            spuriousTransfers();
        }
    }

    private void balanceFileAdjustment() {
        String sql = "update account_balance_file set time_offset = -1 where consensus_timestamp in (:timestamps)";
        jdbcOperations.update(sql, new MapSqlParameterSource("timestamps", getTimestamps()));
    }

    private void spuriousTransfers() {
        String sql = "with spurious_transfer as (" +
                "  update crypto_transfer ct set errata = 'DELETE' from transaction t " +
                "  where t.consensus_timestamp = ct.consensus_timestamp and t.type = 14 and t.result <> 22 and " +
                "  t.consensus_timestamp < 1577836799000000000 and ct.entity_id <> ct.payer_account_id " +
                "  and amount > 0 and ct.entity_id <> 98 and (ct.entity_id < 3 or ct.entity_id > 27) " +
                "  returning ct.*" +
                ")" +
                "update crypto_transfer ct set errata = 'DELETE' from spurious_transfer st " +
                "where ct.consensus_timestamp = st.consensus_timestamp and ct.amount = st.amount * -1 " +
                "and ct.payer_account_id = ct.entity_id";
        jdbcOperations.getJdbcOperations().update(sql);
    }

    private void missingTransactions() throws IOException {
        Set<Long> consensusTimestamps = new HashSet<>();
        var resourceResolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resourceResolver.getResources("classpath*:errata/mainnet/missingtransactions/*.bin");
        recordStreamFileListener.onStart();

        for (Resource resource : resources) {
            String name = resource.getFilename();
            log.info("Loading file: {}", name);

            try (var in = new ValidatedDataInputStream(resource.getInputStream(), name)) {
                byte[] recordBytes = in.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "record");
                byte[] transactionBytes = in.readLengthAndBytes(1, MAX_TRANSACTION_LENGTH, false, "transaction");
                var transactionRecord = TransactionRecord.parseFrom(recordBytes);
                var transaction = Transaction.parseFrom(transactionBytes);

                var recordItem = new RecordItem(transaction, transactionRecord);
                entityRecordItemListener.onItem(recordItem);
                consensusTimestamps.add(recordItem.getConsensusTimestamp());
            } catch (IOException e) {
                recordStreamFileListener.onError();
                throw new FileOperationException("Error parsing errata file " + name, e);
            }
        }

        recordStreamFileListener.onEnd(null);
        var ids = new MapSqlParameterSource("ids", consensusTimestamps);
        jdbcOperations.update("update crypto_transfer set errata = 'INSERT' where consensus_timestamp in (:ids)", ids);
        jdbcOperations.update("update transaction set errata = 'INSERT' where consensus_timestamp in (:ids)", ids);
        log.info("Inserted {} missing transactions", consensusTimestamps.size());
    }

    private Set<Long> getTimestamps() {
        if (!timestamps.isEmpty()) {
            return timestamps;
        }

        synchronized (timestamps) {
            if (!timestamps.isEmpty()) {
                return timestamps;
            }

            try (var reader = new BufferedReader(new InputStreamReader(balanceOffsets.getInputStream()))) {
                String line = reader.readLine();

                while (line != null) {
                    Long timestamp = Long.parseLong(line);
                    timestamps.add(timestamp);
                    line = reader.readLine();
                }
            } catch (Exception e) {
                log.error("Error processing balance file", e);
            }
        }

        return timestamps;
    }

    private boolean isMainnet() {
        return mirrorProperties.getNetwork() == MirrorProperties.HederaNetwork.MAINNET;
    }
}
