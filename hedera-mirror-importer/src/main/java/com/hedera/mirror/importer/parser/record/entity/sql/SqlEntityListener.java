package com.hedera.mirror.importer.parser.record.entity.sql;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.common.base.Stopwatch;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import javax.sql.DataSource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.exception.DuplicateFileException;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.exception.ParserSQLException;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.parser.record.entity.ConditionOnEntityRecordParser;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Log4j2
@Named
@RequiredArgsConstructor
@ConditionOnEntityRecordParser
public class SqlEntityListener implements EntityListener, RecordStreamFileListener {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);

    private final SqlProperties properties;
    private final DataSource dataSource;
    private final RecordFileRepository recordFileRepository;
    private long batch_count = 0;
    // Keeps track of entityIds seen in the current file being processed. This is for optimizing inserts into
    // t_entities table so that insertion of node and treasury ids are not tried for every transaction.
    private Collection<EntityId> entityIds;
    private PreparedStatement sqlInsertTransaction;
    private PreparedStatement sqlInsertEntityId;
    private PreparedStatement sqlInsertTransferList;
    private PreparedStatement sqlInsertNonFeeTransfers;
    private PreparedStatement sqlInsertFileData;
    private PreparedStatement sqlInsertContractResult;
    private PreparedStatement sqlInsertLiveHashes;
    private PreparedStatement sqlInsertTopicMessage;
    private PreparedStatement sqlNotifyTopicMessage;
    private Connection connection;

    @Override
    public void onStart(StreamFileData streamFileData) {
        String fileName = streamFileData.getFilename();
        entityIds = new HashSet<>();
        if (recordFileRepository.findByName(fileName).size() > 0) {
            throw new DuplicateFileException("File already exists in the database: " + fileName);
        }
        try {
            initConnectionAndStatements();
        } catch (Exception e) {
            throw new ParserException("Error setting up connection and statements", e);
        }
    }

    @Override
    public void onEnd(RecordFile recordFile) {
        executeBatches();
        try {
            connection.commit();
            recordFileRepository.save(recordFile);
            closeConnectionAndStatements();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onError() {
        try {
            if (connection != null) {
                connection.rollback();
                closeConnectionAndStatements();
            }
        } catch (SQLException e) {
            log.error("Exception while rolling transaction back", e);
        }
    }

    private void initConnectionAndStatements() throws ParserSQLException {
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false); // do not auto-commit
            connection.setClientInfo("ApplicationName", getClass().getSimpleName());
        } catch (SQLException e) {
            throw new ParserSQLException("Error setting up connection to database", e);
        }
        try {
            sqlInsertTransaction = connection.prepareStatement("INSERT INTO transaction"
                    + " (node_account_id, memo, valid_start_ns, type, payer_account_id, result, " +
                    "consensus_ns, entity_id, charged_tx_fee, initial_balance, valid_duration_seconds, max_fee, " +
                    "transaction_hash, transaction_bytes)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            sqlInsertEntityId = connection.prepareStatement("INSERT INTO t_entities " +
                    "(id, entity_shard, entity_realm, entity_num, fk_entity_type_id) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT DO NOTHING");

            sqlInsertTransferList = connection.prepareStatement("INSERT INTO crypto_transfer"
                    + " (consensus_timestamp, amount, entity_id)"
                    + " VALUES (?, ?, ?)");

            sqlInsertNonFeeTransfers = connection.prepareStatement("insert into non_fee_transfer"
                    + " (consensus_timestamp, amount, entity_id)"
                    + " values (?, ?, ?)");

            sqlInsertFileData = connection.prepareStatement("INSERT INTO t_file_data"
                    + " (consensus_timestamp, file_data)"
                    + " VALUES (?, ?)");

            sqlInsertContractResult = connection.prepareStatement("INSERT INTO t_contract_result"
                    + " (consensus_timestamp, function_params, gas_supplied, call_result, gas_used)"
                    + " VALUES (?, ?, ?, ?, ?)");

            sqlInsertLiveHashes = connection.prepareStatement("INSERT INTO t_livehashes"
                    + " (consensus_timestamp, livehash)"
                    + " VALUES (?, ?)");

            sqlInsertTopicMessage = connection.prepareStatement("insert into topic_message"
                    + " (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number" +
                    ", running_hash_version, chunk_num, chunk_total, payer_account_id, valid_start_timestamp)"
                    + " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            sqlNotifyTopicMessage = connection.prepareStatement("select pg_notify('topic_message', ?)");
        } catch (SQLException e) {
            throw new ParserSQLException("Unable to prepare SQL statements", e);
        }
    }

    private void closeConnectionAndStatements() {
        try {
            sqlInsertTransaction.close();
            sqlInsertEntityId.close();
            sqlInsertTransferList.close();
            sqlInsertNonFeeTransfers.close();
            sqlInsertFileData.close();
            sqlInsertContractResult.close();
            sqlInsertLiveHashes.close();
            sqlInsertTopicMessage.close();
            sqlNotifyTopicMessage.close();
            connection.close();
        } catch (SQLException e) {
            throw new ParserSQLException("Error closing connection", e);
        }
    }

    private void executeBatches() {
        try {
            var transactions = executeBatch(sqlInsertTransaction);
            var entityIds = executeBatch(sqlInsertEntityId);
            var transferLists = executeBatch(sqlInsertTransferList);
            var nonFeeTransfers = executeBatch(sqlInsertNonFeeTransfers);
            var fileData = executeBatch(sqlInsertFileData);
            var contractResult = executeBatch(sqlInsertContractResult);
            var liveHashes = executeBatch(sqlInsertLiveHashes);
            var topicMessages = executeBatch(sqlInsertTopicMessage);
            var topicMessageNotifications = executeBatch(sqlNotifyTopicMessage);
            log.info("Inserted transactions: {}, entities: {}, transfer lists: {}, files: {}, contracts: {}, " +
                            "claims: {}, topic messages: {}, topic notifications: {}, non-fee transfers: {}",
                    transactions, entityIds, transferLists, fileData, contractResult, liveHashes, topicMessages,
                    topicMessageNotifications, nonFeeTransfers);
        } catch (SQLException e) {
            log.error("Error committing sql insert batch ", e);
            throw new ParserSQLException(e);
        }
        batch_count = 0;
    }

    private ExecuteBatchResult executeBatch(PreparedStatement ps) throws SQLException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        var executeResult = ps.executeBatch();
        return new ExecuteBatchResult(executeResult.length, stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        try {
            // Temporary until we convert SQL statements to repository invocations
            if (transaction.getEntityId() != null) {
                sqlInsertTransaction.setLong(F_TRANSACTION.ENTITY_ID.ordinal(), transaction.getEntityId().getId());
            } else {
                sqlInsertTransaction.setObject(F_TRANSACTION.ENTITY_ID.ordinal(), null);
            }
            sqlInsertTransaction.setLong(F_TRANSACTION.NODE_ACCOUNT_ID.ordinal(),
                    transaction.getNodeAccountId().getId());
            sqlInsertTransaction.setBytes(F_TRANSACTION.MEMO.ordinal(), transaction.getMemo());
            sqlInsertTransaction.setLong(F_TRANSACTION.VALID_START_NS.ordinal(), transaction.getValidStartNs());
            sqlInsertTransaction.setInt(F_TRANSACTION.TYPE.ordinal(), transaction.getType());
            sqlInsertTransaction.setLong(F_TRANSACTION.VALID_DURATION_SECONDS.ordinal(),
                    transaction.getValidDurationSeconds());
            sqlInsertTransaction.setLong(F_TRANSACTION.PAYER_ACCOUNT_ID.ordinal(),
                    transaction.getPayerAccountId().getId());
            sqlInsertTransaction.setLong(F_TRANSACTION.RESULT.ordinal(), transaction.getResult());
            sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_NS.ordinal(), transaction.getConsensusNs());
            sqlInsertTransaction.setLong(F_TRANSACTION.CHARGED_TX_FEE.ordinal(), transaction.getChargedTxFee());
            sqlInsertTransaction.setLong(F_TRANSACTION.MAX_FEE.ordinal(), transaction.getMaxFee());
            sqlInsertTransaction.setBytes(F_TRANSACTION.TRANSACTION_HASH.ordinal(), transaction.getTransactionHash());
            sqlInsertTransaction.setBytes(F_TRANSACTION.TRANSACTION_BYTES.ordinal(), transaction.getTransactionBytes());
            sqlInsertTransaction.setLong(F_TRANSACTION.INITIAL_BALANCE.ordinal(), transaction.getInitialBalance());
            sqlInsertTransaction.addBatch();

            if (batch_count == properties.getBatchSize() - 1) {
                // execute any remaining batches
                executeBatches();
            } else {
                batch_count += 1;
            }
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onEntityId(EntityId entityId) throws ImporterException {
        if (entityIds.contains(entityId)) {
            return;
        }
        try {
            sqlInsertEntityId.setLong(F_ENTITY_ID.ID.ordinal(), entityId.getId());
            sqlInsertEntityId.setLong(F_ENTITY_ID.ENTITY_SHARD.ordinal(), entityId.getShardNum());
            sqlInsertEntityId.setLong(F_ENTITY_ID.ENTITY_REALM.ordinal(), entityId.getRealmNum());
            sqlInsertEntityId.setLong(F_ENTITY_ID.ENTITY_NUM.ordinal(), entityId.getEntityNum());
            sqlInsertEntityId.setLong(F_ENTITY_ID.TYPE.ordinal(), entityId.getType());
            sqlInsertEntityId.addBatch();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
        entityIds.add(entityId);
    }

    @Override
    public void onCryptoTransfer(CryptoTransfer cryptoTransfer) throws ImporterException {
        try {
            sqlInsertTransferList.setLong(F_TRANSFERLIST.CONSENSUS_TIMESTAMP.ordinal(),
                    cryptoTransfer.getConsensusTimestamp());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), cryptoTransfer.getAmount());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.ENTITY_ID.ordinal(), cryptoTransfer.getEntityId().getId());
            sqlInsertTransferList.addBatch();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onNonFeeTransfer(NonFeeTransfer nonFeeTransfer) throws ImporterException {
        try {
            sqlInsertNonFeeTransfers.setLong(F_NONFEETRANSFER.CONSENSUS_TIMESTAMP.ordinal(),
                    nonFeeTransfer.getConsensusTimestamp());
            sqlInsertNonFeeTransfers.setLong(F_NONFEETRANSFER.AMOUNT.ordinal(), nonFeeTransfer.getAmount());
            sqlInsertNonFeeTransfers
                    .setLong(F_NONFEETRANSFER.ENTITY_ID.ordinal(), nonFeeTransfer.getEntityId().getId());
            sqlInsertNonFeeTransfers.addBatch();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onTopicMessage(TopicMessage topicMessage) throws ImporterException {
        try {
            sqlInsertTopicMessage.setLong(F_TOPICMESSAGE.CONSENSUS_TIMESTAMP.ordinal(),
                    topicMessage.getConsensusTimestamp());
            sqlInsertTopicMessage.setShort(F_TOPICMESSAGE.REALM_NUM.ordinal(), (short) topicMessage.getRealmNum());
            sqlInsertTopicMessage.setInt(F_TOPICMESSAGE.TOPIC_NUM.ordinal(), topicMessage.getTopicNum());
            sqlInsertTopicMessage.setBytes(F_TOPICMESSAGE.MESSAGE.ordinal(), topicMessage.getMessage());
            sqlInsertTopicMessage.setBytes(F_TOPICMESSAGE.RUNNING_HASH.ordinal(), topicMessage.getRunningHash());
            sqlInsertTopicMessage.setLong(F_TOPICMESSAGE.SEQUENCE_NUMBER.ordinal(), topicMessage.getSequenceNumber());
            sqlInsertTopicMessage
                    .setInt(F_TOPICMESSAGE.RUNNING_HASH_VERSION.ordinal(), topicMessage.getRunningHashVersion());

            // handle nullable chunk info columns
            if (topicMessage.getChunkNum() != null) {
                sqlInsertTopicMessage.setInt(F_TOPICMESSAGE.CHUNK_NUM.ordinal(), topicMessage.getChunkNum());
                sqlInsertTopicMessage.setInt(F_TOPICMESSAGE.CHUNK_TOTAL.ordinal(), topicMessage.getChunkTotal());
                if (topicMessage.getPayerAccountId() != null) {
                    sqlInsertTopicMessage.setLong(F_TOPICMESSAGE.PAYER_ACCOUNT_ID.ordinal(),
                            topicMessage.getPayerAccountId().getId());
                } else {
                    sqlInsertTopicMessage.setObject(F_TOPICMESSAGE.PAYER_ACCOUNT_ID.ordinal(), null);
                }
                sqlInsertTopicMessage
                        .setLong(F_TOPICMESSAGE.VALID_START_TIMESTAMP.ordinal(), topicMessage.getValidStartTimestamp());
            } else {
                sqlInsertTopicMessage.setNull(F_TOPICMESSAGE.CHUNK_NUM.ordinal(), Types.SMALLINT);
                sqlInsertTopicMessage.setNull(F_TOPICMESSAGE.CHUNK_TOTAL.ordinal(), Types.SMALLINT);
                sqlInsertTopicMessage.setObject(F_TOPICMESSAGE.PAYER_ACCOUNT_ID.ordinal(), null);
                sqlInsertTopicMessage.setNull(F_TOPICMESSAGE.VALID_START_TIMESTAMP.ordinal(), Types.BIGINT);
            }

            sqlInsertTopicMessage.addBatch();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }

        try {
            if (properties.isNotifyTopicMessage()) {
                String json = OBJECT_MAPPER.writeValueAsString(topicMessage);
                sqlNotifyTopicMessage.setString(1, json);
                sqlNotifyTopicMessage.addBatch();
            }
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    @Override
    public void onContractResult(ContractResult contractResult) throws ImporterException {
        try {
            sqlInsertContractResult.setLong(F_CONTRACT_RESULT.CONSENSUS_TIMESTAMP.ordinal(),
                    contractResult.getConsensusTimestamp());
            sqlInsertContractResult
                    .setBytes(F_CONTRACT_RESULT.FUNCTION_PARAMS.ordinal(), contractResult.getFunctionParameters());
            sqlInsertContractResult.setLong(F_CONTRACT_RESULT.GAS_SUPPLIED.ordinal(), contractResult.getGasSupplied());
            sqlInsertContractResult.setBytes(F_CONTRACT_RESULT.CALL_RESULT.ordinal(), contractResult.getCallResult());
            sqlInsertContractResult.setLong(F_CONTRACT_RESULT.GAS_USED.ordinal(), contractResult.getGasUsed());
            sqlInsertContractResult.addBatch();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onFileData(FileData fileData) throws ImporterException {
        try {
            sqlInsertFileData.setLong(F_FILE_DATA.CONSENSUS_TIMESTAMP.ordinal(), fileData.getConsensusTimestamp());
            sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), fileData.getFileData());
            sqlInsertFileData.addBatch();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    @Override
    public void onLiveHash(LiveHash liveHash) throws ImporterException {
        try {
            sqlInsertLiveHashes
                    .setLong(F_LIVEHASHES.CONSENSUS_TIMESTAMP.ordinal(), liveHash.getConsensusTimestamp());
            sqlInsertLiveHashes.setBytes(F_LIVEHASHES.LIVEHASH.ordinal(), liveHash.getLivehash());
            sqlInsertLiveHashes.addBatch();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
        }
    }

    enum F_TRANSACTION {
        ZERO, // column indices start at 1, this creates the necessary offset
        NODE_ACCOUNT_ID, MEMO, VALID_START_NS, TYPE, PAYER_ACCOUNT_ID,
        RESULT, CONSENSUS_NS, ENTITY_ID, CHARGED_TX_FEE, INITIAL_BALANCE, VALID_DURATION_SECONDS,
        MAX_FEE, TRANSACTION_HASH, TRANSACTION_BYTES
    }

    enum F_ENTITY_ID {
        ZERO, // column indices start at 1, this creates the necessary offset
        ID, ENTITY_SHARD, ENTITY_REALM, ENTITY_NUM, TYPE
    }

    enum F_TRANSFERLIST {
        ZERO // column indices start at 1, this creates the necessary offset
        , CONSENSUS_TIMESTAMP, AMOUNT, ENTITY_ID
    }

    enum F_NONFEETRANSFER {
        ZERO // column indices start at 1, this creates the necessary offset
        , CONSENSUS_TIMESTAMP, AMOUNT, ENTITY_ID
    }

    enum F_TOPICMESSAGE {
        ZERO // column indices start at 1, this creates the necessary offset
        , CONSENSUS_TIMESTAMP, REALM_NUM, TOPIC_NUM, MESSAGE, RUNNING_HASH, SEQUENCE_NUMBER, RUNNING_HASH_VERSION,
        CHUNK_NUM, CHUNK_TOTAL, PAYER_ACCOUNT_ID, VALID_START_TIMESTAMP
    }

    enum F_FILE_DATA {
        ZERO, CONSENSUS_TIMESTAMP, FILE_DATA
    }

    enum F_CONTRACT_RESULT {
        ZERO, CONSENSUS_TIMESTAMP, FUNCTION_PARAMS, GAS_SUPPLIED, CALL_RESULT, GAS_USED
    }

    enum F_LIVEHASHES {
        ZERO, CONSENSUS_TIMESTAMP, LIVEHASH
    }

    @Data
    @AllArgsConstructor
    private static class ExecuteBatchResult {
        int numRows;
        long timeTakenInMs;

        @Override
        public String toString() {
            return numRows + " (" + timeTakenInMs + "ms)";
        }
    }
}
