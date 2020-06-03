package com.hedera.datagenerator.domain.writer;
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
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.util.Utility;

/**
 * Generates CSV files to load into PostgreSQL using COPY command.
 * <p>
 * Generated data:
 * <p>
 * <p>
 * Test data for tables: <br/>
 * <ul>
 *   <li>transaction</li>
 *   <li>file_data</li>
 *   <li>crypto_transfer</li>
 *   <li>t_entities</li>
 *   <li>topic_message</li>
 *   <li>account_balances</li>
 * </ul>
 * <p>
 * Tables already setup by migrations:
 * <ul>
 *   <li>t_transaction_types</li>
 *   <li>t_transaction_results</li>
 *   <li>t_entity_types</li>
 * </ul>
 * <p>
 * Tables left empty:
 * <ul>
 *   <li>record_file</li>
 *   <li>contract_result (empty until contract transaction generator is added)</li>
 *   <li>t_events (events are disabled in production)</li>
 *   <li>live_hash (crypto claims not implemented in services,
 *     https://github.com/swirlds/services-hedera/issues/1706)</li>
 *   <li>account_balance_sets</li>
 *   <li>t_application_status</li>
 * </ul>
 * <p>
 * Don't care tables:
 * <ul>
 *   <li>entity_types</li>
 *   <li>flyway_schema_history</li>
 * </ul>
 */
// TODO: Replace this by CopySqlEntityListener
@Named
@Log4j2
public class PostgresCSVDomainWriter implements DomainWriter {

    private final CSVPrinter transactionsWriter;
    private final CSVPrinter cryptoTransferListsWriter;
    private final CSVPrinter entitiesWriter;
    private final CSVPrinter fileDataWriter;
    private final CSVPrinter topicMessageWriter;
    private final CSVPrinter accountBalancesWriter;

    PostgresCSVDomainWriter(Properties properties) throws IOException {
        String outputDir = properties.outputDir;
        Utility.ensureDirectory(Path.of(outputDir));
        log.info("Writing CSV files to {}", outputDir);
        transactionsWriter = getTransactionsCSVPrinter(outputDir);
        cryptoTransferListsWriter = getCryptoTransferListsCSVPrinter(outputDir);
        entitiesWriter = getEntitiesCSVPrinter(outputDir);
        fileDataWriter = getFileDataCSVPrinter(outputDir);
        topicMessageWriter = getTopicMessageCSVPrinter(outputDir);
        accountBalancesWriter = getAccountBalancesCSVPrinter(outputDir);
    }

    private static CSVPrinter getTransactionsCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "transaction")),
                CSVFormat.DEFAULT.withHeader(
                        "node_account_id", "memo", "payer_account_id", "charged_tx_fee", "initial_balance", "entity_id",
                        "valid_start_ns", "consensus_ns", "valid_duration_seconds", "max_fee", "transaction_hash",
                        "result", "type", "transaction_bytes"));
    }

    private static CSVPrinter getEntitiesCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "t_entities")),
                CSVFormat.DEFAULT.withHeader(
                        "id", "entity_num", "entity_realm", "entity_shard", "fk_entity_type_id", "auto_renew_period",
                        "key", "proxy_account_id", "deleted", "exp_time_ns", "ed25519_public_key_hex", "submit_key",
                        "memo", "auto_renew_account_id"));
    }

    private static CSVPrinter getCryptoTransferListsCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "crypto_transfer")),
                CSVFormat.DEFAULT.withHeader("entity_id", "consensus_timestamp", "amount"));
    }

    private static CSVPrinter getFileDataCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "file_data")),
                CSVFormat.DEFAULT.withHeader("file_data", "consensus_timestamp"));
    }

    private static CSVPrinter getTopicMessageCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "topic_message")),
                CSVFormat.DEFAULT.withHeader("consensus_timestamp", "realm_num", "topic_num", "message",
                        "running_hash", "sequence_number", "running_hash_version"));
    }

    private static CSVPrinter getAccountBalancesCSVPrinter(String outputDir) throws IOException {
        return new CSVPrinter(
                Files.newBufferedWriter(Paths.get(outputDir, "account_balances")),
                CSVFormat.DEFAULT.withHeader("consensus_timestamp", "balance", "account_realm_num", "account_num"));
    }

    private static String toHex(byte[] data) {
        if (data == null) {
            return null;
        } else {
            return "\\x" + Hex.encodeHexString(data);
        }
    }

    @Override
    public void close() throws IOException {
        transactionsWriter.close();
        cryptoTransferListsWriter.close();
        entitiesWriter.close();
        fileDataWriter.close();
        topicMessageWriter.close();
        accountBalancesWriter.close();
    }

    @Override
    public void addTransaction(Transaction transaction) {
        try {
            transactionsWriter.printRecord(
                    transaction.getNodeAccountId(), toHex(transaction.getMemo()), transaction.getPayerAccountId().getId(),
                    transaction.getChargedTxFee(), transaction.getInitialBalance(), transaction.getEntityId(),
                    transaction.getValidStartNs(), transaction.getConsensusNs(), transaction.getValidDurationSeconds(),
                    transaction.getMaxFee(), toHex(transaction.getTransactionHash()), transaction.getResult(),
                    transaction.getType(), toHex(transaction.getTransactionBytes()));
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
                    entity.getEntityTypeId(), entity.getAutoRenewPeriod(), toHex(entity.getKey()),
                    entity.getProxyAccountId(), entity.isDeleted(), entity.getExpiryTimeNs(),
                    entity.getEd25519PublicKeyHex(), toHex(entity.getSubmitKey()), entity.getMemo(),
                    entity.getAutoRenewAccountId());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.trace("added entity id {}", entity.getId());
    }

    @Override
    public void addCryptoTransfer(CryptoTransfer cryptoTransfer) {
        try {
            cryptoTransferListsWriter.printRecord(cryptoTransfer.getEntityId(),
                    cryptoTransfer.getConsensusTimestamp(), cryptoTransfer.getAmount());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log.trace("added crypto transfer for entity_num = {}", cryptoTransfer.getEntityId());
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
    public void addTopicMessage(TopicMessage topicMessage) {
        try {
            topicMessageWriter.printRecord(topicMessage.getConsensusTimestamp(), topicMessage.getRealmNum(),
                    topicMessage.getTopicNum(), toHex(topicMessage.getMessage()), toHex(topicMessage.getRunningHash()),
                    topicMessage.getSequenceNumber());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addAccountBalances(long consensusNs, long balance, EntityId account) {
        try {
            accountBalancesWriter.printRecord(consensusNs, balance, account.getRealmNum(), account.getEntityNum());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Data
    @Named
    @ConfigurationProperties("hedera.mirror.datagenerator.output.postgres.csv")
    public static class Properties {
        private String outputDir;
    }
}
