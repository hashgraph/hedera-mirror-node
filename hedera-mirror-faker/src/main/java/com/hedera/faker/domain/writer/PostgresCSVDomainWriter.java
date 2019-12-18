package com.hedera.faker.domain.writer;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Named;

import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.util.Utility;

/**
 * Generates CSV files to load into PostgresSQL using COPY command.
 * <p>
 * Generated data:
 * <p>
 * Fake data for tables: - t_transactions - t_file_data - t_cryptotransferlists - t_entities - account_balances
 * <p>
 * Fake 'constant' data for tables: - t_record_files : single row with id 0.
 * <p>
 * Tables already setup by migrations: - t_transaction_types - t_transaction_results - t_entity_types
 * <p>
 * Tables left empty: - t_contract_result (empty until contract transaction generator is added) - t_events (events are
 * disabled in production) - t_livehashes (crypto claims not implemented in services,
 * https://github.com/swirlds/services-hedera/issues/1706) - account_balance_sets - t_application_status
 * <p>
 * Don't care tables: - entity_types - flyway_schema_history
 */
@Named
@Log4j2
public class PostgresCSVDomainWriter implements DomainWriter {

    private final CSVPrinter transactionsWriter;
    private final CSVPrinter cryptoTransferListsWriter;
    private final CSVPrinter entitiesWriter;
    private final CSVPrinter fileDataWriter;
    private final CSVPrinter accountBalancesWriter;

    PostgresCSVDomainWriter(Properties properties) throws IOException {
        String outputDir = properties.outputDir;
        Utility.ensureDirectory(Path.of(outputDir));
        log.info("Writing CSV files to {}", outputDir);
        transactionsWriter = getTransactionsCSVPrinter(outputDir);
        cryptoTransferListsWriter = getCryptoTransferListsCSVPrinter(outputDir);
        entitiesWriter = getEntitiesCSVPrinter(outputDir);
        fileDataWriter = getFileDataCSVPrinter(outputDir);
        accountBalancesWriter = getAccountBalancesCSVPrinter(outputDir);
        writeRecordFilesCSV(outputDir);
    }

    private static CSVPrinter getTransactionsCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "t_transactions")),
                CSVFormat.DEFAULT.withHeader(
                        "fk_node_acc_id", "memo", "fk_payer_acc_id", "charged_tx_fee", "initial_balance",
                        "fk_cud_entity_id", "fk_rec_file_id", "valid_start_ns", "consensus_ns",
                        "valid_duration_seconds", "max_fee", "transaction_hash", "result", "type",
                        "transaction_bytes"));
    }

    private static CSVPrinter getEntitiesCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "t_entities")),
                CSVFormat.DEFAULT.withHeader(
                        "id", "entity_num", "entity_realm", "entity_shard", "fk_entity_type_id", "exp_time_seconds",
                        "exp_time_nanos", "auto_renew_period", "admin_key__deprecated", "key", "fk_prox_acc_id",
                        "deleted", "exp_time_ns", "ed25519_public_key_hex"));
    }

    private static CSVPrinter getCryptoTransferListsCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "t_cryptotransferlists")),
                CSVFormat.DEFAULT.withHeader("amount", "consensus_timestamp", "realm_num", "entity_num"));
    }

    private static CSVPrinter getFileDataCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "t_file_data")),
                CSVFormat.DEFAULT.withHeader("file_data", "consensus_timestamp"));
    }

    private static CSVPrinter getAccountBalancesCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "account_balances")),
                CSVFormat.DEFAULT.withHeader("consensus_timestamp", "balance", "account_realm_num", "account_num"));
    }

    private static void writeRecordFilesCSV(String outputDir) throws IOException {
        CSVPrinter recordFilesWriter = new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "t_record_files")),
                CSVFormat.DEFAULT.withHeader("id", "name", "load_start", "load_end", "file_hash", "prev_hash"));
        recordFilesWriter.printRecord(
                0, "./data/recordstreams/valid/2000-01-01T00_00_01.000001Z.rcd", 1573705657, 1573705657,
                "420fffe68fcd2a1eadcce589fdf9565bcf5a269d02232fe07cdc565b3b6f76ce46a9418ddc1bbe051d4894e04d091f8e",
                null);
        recordFilesWriter.close();
    }

    @Override
    public void close() throws IOException {
        transactionsWriter.close();
        cryptoTransferListsWriter.close();
        entitiesWriter.close();
        accountBalancesWriter.close();
        fileDataWriter.close();
    }

    @Override
    public void addTransaction(Transaction transaction) {
        try {
            transactionsWriter.printRecord(
                    transaction.getNodeAccountId(), toHex(transaction.getMemo()), transaction.getPayerAccountId(),
                    transaction.getChargedTxFee(), transaction.getInitialBalance(), transaction.getEntityId(),
                    /* record file id */ 0, transaction.getValidStartNs(), transaction.getConsensusNs(),
                    transaction.getValidDurationSeconds(), transaction.getMaxFee(),
                    toHex(transaction.getTransactionHash()), transaction.getResult(), transaction.getType(),
                    toHex(transaction.getTransactionBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.trace("added transaction at timestamp {}", transaction.getConsensusNs());
    }

    @Override
    public void addEntity(Entities entity) {
        try {
            entitiesWriter.printRecord(
                    entity.getId(), entity.getEntityNum(), entity.getEntityRealm(), entity.getEntityShard(),
                    entity.getEntityTypeId(), entity.getExpiryTimeSeconds(), entity.getExpiryTimeNanos(),
                    entity.getAutoRenewPeriod(), null, toHex(entity.getKey()), entity.getProxyAccountId(),
                    entity.isDeleted(), entity.getExpiryTimeNs(), entity.getEd25519PublicKeyHex());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.trace("added entity {}", entity.getId());
    }

    @Override
    public void addCryptoTransfer(CryptoTransfer cryptoTransfer) {
        try {
            cryptoTransferListsWriter.printRecord(
                    cryptoTransfer.getAmount(), cryptoTransfer.getConsensusTimestamp(), cryptoTransfer.getRealmNum(),
                    cryptoTransfer.getEntityNum());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.trace("added crypto transfer for entity_num = {}", cryptoTransfer.getEntityNum());
    }

    @Override
    public void addFileData(FileData fileData) {
        try {
            fileDataWriter.printRecord(toHex(fileData.getFileData()), fileData.getConsensusTimestamp());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.trace("added file data size {} at timestamp {}", fileData.getFileData().length,
                fileData.getConsensusTimestamp());
    }

    @Override
    public void addAccountBalances(long consensusNs, long balance, long accountNum) {
        try {
            accountBalancesWriter.printRecord(consensusNs, balance, 0, accountNum);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String toHex(byte[] data) {
        if (data == null) {
            return null;
        } else {
            return "\\x" + Hex.encodeHexString(data);
        }
    }

    @Data
    @Named
    @ConfigurationProperties("faker.output.postgres.csv")
    public static class Properties {
        private String outputDir;
    }
}
