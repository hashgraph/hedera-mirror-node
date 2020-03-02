package com.hedera.mirror.importer.parser.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2020 Hedera Hashgraph, LLC
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.domain.ContractResult;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.FileData;
import com.hedera.mirror.importer.domain.LiveHash;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.domain.TopicMessage;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.exception.ImporterException;
import com.hedera.mirror.importer.exception.ParserSQLException;

import org.apache.commons.lang3.NotImplementedException;

@Log4j2
@Named
@RequiredArgsConstructor
public class PostgresWritingRecordParsedItemHandler implements RecordParsedItemHandler {
    private long batch_count = 0;
    private PreparedStatement sqlInsertTransaction;
    private PreparedStatement sqlInsertTransferList;
    private PreparedStatement sqlInsertNonFeeTransfers;
    private PreparedStatement sqlInsertFileData;
    private PreparedStatement sqlInsertContractResult;
    private PreparedStatement sqlInsertLiveHashes;
    private PreparedStatement sqlInsertTopicMessage;
    private final PostgresWriterProperties properties;

    void initSqlStatements(Connection connection) throws ParserSQLException {
        try {
            sqlInsertTransaction = connection.prepareStatement("INSERT INTO t_transactions"
                    + " (fk_node_acc_id, memo, valid_start_ns, type, fk_payer_acc_id"
                    + ", result, consensus_ns, fk_cud_entity_id, charged_tx_fee"
                    + ", initial_balance, fk_rec_file_id, valid_duration_seconds, max_fee"
                    + ", transaction_hash, transaction_bytes)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            sqlInsertTransferList = connection.prepareStatement("INSERT INTO t_cryptotransferlists"
                    + " (consensus_timestamp, amount, realm_num, entity_num)"
                    + " VALUES (?, ?, ?, ?)");

            sqlInsertNonFeeTransfers = connection.prepareStatement("insert into non_fee_transfers"
                    + " (consensus_timestamp, amount, realm_num, entity_num)"
                    + " values (?, ?, ?, ?)");

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
                    + " (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number)"
                    + " values (?, ?, ?, ?, ?, ?)");
        } catch (SQLException e) {
            throw new ParserSQLException("Unable to prepare SQL statements", e);
        }
    }

    public void finish() {
        closeStatements();
    }

    @Override
    public void onFileComplete() {
        executeBatches();
    }

    private void closeStatements() {
        try {
            sqlInsertTransaction.close();
            sqlInsertTransferList.close();
            sqlInsertNonFeeTransfers.close();
            sqlInsertFileData.close();
            sqlInsertContractResult.close();
            sqlInsertLiveHashes.close();
            sqlInsertTopicMessage.close();
        } catch (SQLException e) {
            throw new ParserSQLException("Error closing connection", e);
        }
    }

    void executeBatches() {
        try {
            int[] transactions = sqlInsertTransaction.executeBatch();
            int[] transferLists = sqlInsertTransferList.executeBatch();
            int[] nonFeeTransfers = sqlInsertNonFeeTransfers.executeBatch();
            int[] fileData = sqlInsertFileData.executeBatch();
            int[] contractResult = sqlInsertContractResult.executeBatch();
            int[] liveHashes = sqlInsertLiveHashes.executeBatch();
            int[] topicMessages = sqlInsertTopicMessage.executeBatch();
            log.info("Inserted {} transactions, {} transfer lists, {} files, {} contracts, {} claims, {} topic " +
                            "messages, " +
                            "{} non-fee transfers",
                    transactions.length, transferLists.length, fileData.length, contractResult.length,
                    liveHashes.length, topicMessages.length, nonFeeTransfers.length);
        } catch (SQLException e) {
            log.error("Error committing sql insert batch ", e);
            throw new ParserSQLException(e);
        }
        batch_count = 0;
    }

    @Override
    public void onTransaction(Transaction transaction) throws ImporterException {
        try {
            // Temporary until we convert SQL statements to repository invocations
            if (transaction.getEntity() != null) {
                sqlInsertTransaction.setLong(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), transaction.getEntity().getId());
            } else {
                sqlInsertTransaction.setObject(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), null);
            }
            sqlInsertTransaction.setLong(F_TRANSACTION.FK_REC_FILE_ID.ordinal(), transaction.getRecordFileId());
            sqlInsertTransaction.setLong(F_TRANSACTION.FK_NODE_ACCOUNT_ID.ordinal(), transaction.getNodeAccountId());
            sqlInsertTransaction.setBytes(F_TRANSACTION.MEMO.ordinal(), transaction.getMemo());
            sqlInsertTransaction.setLong(F_TRANSACTION.VALID_START_NS.ordinal(), transaction.getValidStartNs());
            sqlInsertTransaction.setInt(F_TRANSACTION.TYPE.ordinal(), transaction.getType());
            sqlInsertTransaction.setLong(F_TRANSACTION.VALID_DURATION_SECONDS.ordinal(),
                    transaction.getValidDurationSeconds());
            sqlInsertTransaction.setLong(F_TRANSACTION.FK_PAYER_ACCOUNT_ID.ordinal(), transaction.getPayerAccountId());
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
    public void onCryptoTransferList(CryptoTransfer cryptoTransfer) throws ImporterException {
        try {
            sqlInsertTransferList.setLong(F_TRANSFERLIST.CONSENSUS_TIMESTAMP.ordinal(),
                    cryptoTransfer.getConsensusTimestamp());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.REALM_NUM.ordinal(), cryptoTransfer.getRealmNum());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.ENTITY_NUM.ordinal(), cryptoTransfer.getEntityNum());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), cryptoTransfer.getAmount());
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
            sqlInsertNonFeeTransfers.setLong(F_NONFEETRANSFER.REALM_NUM.ordinal(), nonFeeTransfer.getRealmNum());
            sqlInsertNonFeeTransfers.setLong(F_NONFEETRANSFER.ENTITY_NUM.ordinal(), nonFeeTransfer.getEntityNum());
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
            sqlInsertTopicMessage.addBatch();
        } catch (SQLException e) {
            throw new ParserSQLException(e);
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

    @Override
    public void onError(Throwable e) {
        throw new NotImplementedException("onError not implemented");
    }

    enum F_TRANSACTION {
        ZERO // column indices start at 1, this creates the necessary offset
        , FK_NODE_ACCOUNT_ID, MEMO, VALID_START_NS, TYPE, FK_PAYER_ACCOUNT_ID, RESULT, CONSENSUS_NS,
        CUD_ENTITY_ID, CHARGED_TX_FEE, INITIAL_BALANCE, FK_REC_FILE_ID, VALID_DURATION_SECONDS, MAX_FEE,
        TRANSACTION_HASH, TRANSACTION_BYTES
    }

    enum F_TRANSFERLIST {
        ZERO // column indices start at 1, this creates the necessary offset
        , CONSENSUS_TIMESTAMP, AMOUNT, REALM_NUM, ENTITY_NUM
    }

    enum F_NONFEETRANSFER {
        ZERO // column indices start at 1, this creates the necessary offset
        , CONSENSUS_TIMESTAMP, AMOUNT, REALM_NUM, ENTITY_NUM
    }

    enum F_TOPICMESSAGE {
        ZERO // column indices start at 1, this creates the necessary offset
        , CONSENSUS_TIMESTAMP, REALM_NUM, TOPIC_NUM, MESSAGE, RUNNING_HASH, SEQUENCE_NUMBER
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
}
