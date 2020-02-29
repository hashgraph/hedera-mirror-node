package com.hedera.mirror.importer.parser.record;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddClaimTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;
import java.util.function.Predicate;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityType;
import com.hedera.mirror.importer.domain.NonFeeTransfer;
import com.hedera.mirror.importer.parser.CommonParserProperties;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.EntityTypeRepository;
import com.hedera.mirror.importer.util.DatabaseUtilities;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class RecordFileLogger {
    public static Connection connect = null;
    private static RecordParserProperties parserProperties;
    private static NetworkAddressBook networkAddressBook;
    private static EntityRepository entityRepository;
    private static EntityTypeRepository entityTypeRepository;
    private static NonFeeTransferExtractionStrategy nonFeeTransfersExtractor;
    private static Predicate<com.hedera.mirror.importer.domain.Transaction> transactionFilter;
    private static PostgresWritingRecordParsedItemHandler postgresWriter;

    private static long fileId = 0;
    private static long BATCH_SIZE = 100;
    private static long batch_count = 0;

    private static PreparedStatement sqlInsertTransaction;
    private static PreparedStatement sqlInsertFileData;
    private static PreparedStatement sqlInsertContractCall;
    private static PreparedStatement sqlInsertClaimData;
    private static PreparedStatement sqlInsertTopicMessage;

    public RecordFileLogger(CommonParserProperties commonParserProperties, RecordParserProperties parserProperties,
                            NetworkAddressBook networkAddressBook, EntityRepository entityRepository,
                            EntityTypeRepository entityTypeRepository,
                            NonFeeTransferExtractionStrategy nonFeeTransfersExtractor,
                            PostgresWritingRecordParsedItemHandler postgresWriter) {
        RecordFileLogger.parserProperties = parserProperties;
        RecordFileLogger.networkAddressBook = networkAddressBook;
        RecordFileLogger.entityRepository = entityRepository;
        RecordFileLogger.entityTypeRepository = entityTypeRepository;
        RecordFileLogger.nonFeeTransfersExtractor = nonFeeTransfersExtractor;
        RecordFileLogger.postgresWriter = postgresWriter;
        transactionFilter = commonParserProperties.getFilter();
    }

    static long getFileId() {
        return fileId;
    }

    static void setBatchSize(long batchSize) {
        BATCH_SIZE = batchSize;
    }

    public static boolean start() {
        batch_count = 0;

        connect = DatabaseUtilities.openDatabase(connect);

        if (connect == null) {
            log.error("Unable to connect to database");
            return false;
        }
        // do not auto-commit
        try {
            connect.setAutoCommit(false);
        } catch (SQLException e) {
            log.error("Unable to set connection to not auto commit", e);
            return false;
        }

        try {
            sqlInsertTransaction = connect.prepareStatement("INSERT INTO t_transactions"
                    + " (fk_node_acc_id, memo, valid_start_ns, type, fk_payer_acc_id"
                    + ", result, consensus_ns, fk_cud_entity_id, charged_tx_fee"
                    + ", initial_balance, fk_rec_file_id, valid_duration_seconds, max_fee"
                    + ", transaction_hash, transaction_bytes)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            postgresWriter.initSqlStatements(connect);

            sqlInsertFileData = connect.prepareStatement("INSERT INTO t_file_data"
                    + " (consensus_timestamp, file_data)"
                    + " VALUES (?, ?)");

            sqlInsertContractCall = connect.prepareStatement("INSERT INTO t_contract_result"
                    + " (consensus_timestamp, function_params, gas_supplied, call_result, gas_used)"
                    + " VALUES (?, ?, ?, ?, ?)");

            sqlInsertClaimData = connect.prepareStatement("INSERT INTO t_livehashes"
                    + " (consensus_timestamp, livehash)"
                    + " VALUES (?, ?)");

            sqlInsertTopicMessage = connect.prepareStatement("insert into topic_message"
                    + " (consensus_timestamp, realm_num, topic_num, message, running_hash, sequence_number)"
                    + " values (?, ?, ?, ?, ?, ?)");
        } catch (SQLException e) {
            log.error("Unable to prepare SQL statements", e);
            return false;
        }

        return true;
    }

    public static boolean finish() {
        try {
            sqlInsertFileData.close();
            sqlInsertTransaction.close();
            sqlInsertContractCall.close();
            sqlInsertClaimData.close();
            sqlInsertTopicMessage.close();
            postgresWriter.finish();

            connect = DatabaseUtilities.closeDatabase(connect);
            return false;
        } catch (SQLException e) {
            log.error("Error closing connection", e);
        }
        return true;
    }

    public static INIT_RESULT initFile(String fileName) {
        try {
            fileId = 0;

            try (CallableStatement fileCreate = connect.prepareCall("{? = call f_file_create( ? ) }")) {
                fileCreate.registerOutParameter(1, Types.BIGINT);
                fileCreate.setString(2, fileName);
                fileCreate.execute();
                fileId = fileCreate.getLong(1);
            }

            if (fileId == 0) {
                log.trace("File {} already exists in the database.", fileName);
                return INIT_RESULT.SKIP;
            } else {
                log.trace("Added file {} to the database.", fileName);
                return INIT_RESULT.OK;
            }
        } catch (SQLException e) {
            log.error("Error saving file in database: {}", fileName, e);
        }
        return INIT_RESULT.FAIL;
    }

    public static void completeFile(String fileHash, String previousHash) throws SQLException {
        try (CallableStatement fileClose = connect.prepareCall("{call f_file_complete( ?, ?, ? ) }")) {
            // execute any remaining batches
            executeBatches();
            postgresWriter.onFileComplete();

            // update the file to processed

            fileClose.setLong(1, fileId);

            if (Utility.hashIsEmpty(fileHash)) {
                fileClose.setObject(2, null);
            } else {
                fileClose.setString(2, fileHash);
            }

            if (Utility.hashIsEmpty(previousHash)) {
                fileClose.setObject(3, null);
            } else {
                fileClose.setString(3, previousHash);
            }

            fileClose.execute();
            // commit the changes to the database
            connect.commit();
        }
    }

    public static void rollback() {
        try {
            connect.rollback();
        } catch (SQLException e) {
            log.error("Exception while rolling transaction back", e);
        }
    }

    public static boolean isSuccessful(TransactionRecord transactionRecord) {
        return ResponseCodeEnum.SUCCESS == transactionRecord.getReceipt().getStatus();
    }

    public static void storeRecord(Transaction transaction, TransactionRecord txRecord) throws Exception {
        storeRecord(transaction, txRecord, null);
    }

    public static void storeRecord(Transaction transaction, TransactionRecord txRecord, byte[] rawBytes) throws Exception {
        TransactionBody body;

        if (transaction.hasBody()) {
            body = transaction.getBody();
        } else {
            body = TransactionBody.parseFrom(transaction.getBodyBytes());
        }

        log.trace("Storing transaction body: {}", () -> Utility.printProtoMessage(body));
        long initialBalance = 0;

        Entities entity = null; // Entity used when t_entities row must be updated.
        EntityId entityId = null; // Entity ID simply used for reference purposes (in the transaction object)
        EntityId proxyEntityId = null;

        /**
         * If the transaction wasn't successful don't update the entity.
         * Still include the transfer list.
         * Still create the entity (empty) and reference it from t_transactions, as it would have been validated
         * to exist in preconsensus checks.
         * Don't update any attributes of the entity.
         */
        boolean doUpdateEntity = isSuccessful(txRecord);

        if (body.hasContractCall()) {
            if (body.getContractCall().hasContractID()) {
                entityId = getEntityId(body.getContractCall().getContractID());
            }
        } else if (body.hasContractCreateInstance()) {
            if (txRecord.getReceipt().hasContractID()) { // implies SUCCESS
                ContractCreateTransactionBody txMessage = body.getContractCreateInstance();
                entity = getEntity(txRecord.getReceipt().getContractID());
                proxyEntityId = getEntityId(txMessage.getProxyAccountID());

                if (txMessage.hasAutoRenewPeriod()) {
                    entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
                }

                // Can't clear memo on contracts. 0 length indicates no change
                if (txMessage.getMemo() != null && txMessage.getMemo().length() > 0) {
                    entity.setMemo(txMessage.getMemo());
                }

                if (txMessage.hasAdminKey()) {
                    entity.setKey(txMessage.getAdminKey().toByteArray());
                }
            }

            initialBalance = body.getContractCreateInstance().getInitialBalance();
        } else if (body.hasContractDeleteInstance()) {
            if (body.getContractDeleteInstance().hasContractID()) {
                entity = getEntity(body.getContractDeleteInstance().getContractID());
                if (doUpdateEntity) {
                    entity.setDeleted(true);
                }
            }
        } else if (body.hasContractUpdateInstance()) {
            ContractUpdateTransactionBody txMessage = body.getContractUpdateInstance();
            entity = getEntity(txMessage.getContractID());

            if (doUpdateEntity) {
                proxyEntityId = getEntityId(txMessage.getProxyAccountID());

                if (txMessage.hasExpirationTime()) {
                    entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
                }

                if (txMessage.hasAutoRenewPeriod()) {
                    entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
                }

                if (txMessage.hasAdminKey()) {
                    entity.setKey(txMessage.getAdminKey().toByteArray());
                }

                // Can't clear memo on contracts. 0 length indicates no change
                if (txMessage.getMemo() != null && txMessage.getMemo().length() > 0) {
                    entity.setMemo(txMessage.getMemo());
                }
            }
        } else if (body.hasCryptoAddClaim()) {
            if (body.getCryptoAddClaim().hasClaim()) {
                if (body.getCryptoAddClaim().getClaim().hasAccountID()) {
                    entityId = getEntityId(body.getCryptoAddClaim().getClaim().getAccountID());
                }
            }
        } else if (body.hasCryptoCreateAccount()) {
            if (txRecord.getReceipt().hasAccountID()) { // Implies SUCCESS
                CryptoCreateTransactionBody txMessage = body.getCryptoCreateAccount();
                proxyEntityId = getEntityId(txMessage.getProxyAccountID());
                entity = getEntity(txRecord.getReceipt().getAccountID());

                if (txMessage.hasAutoRenewPeriod()) {
                    entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
                }

                if (txMessage.hasKey()) {
                    entity.setKey(txMessage.getKey().toByteArray());
                }
            }

            initialBalance = body.getCryptoCreateAccount().getInitialBalance();
        } else if (body.hasCryptoDelete()) {
            if (body.getCryptoDelete().hasDeleteAccountID()) {
                entity = getEntity(body.getCryptoDelete().getDeleteAccountID());
                if (doUpdateEntity) {
                    entity.setDeleted(true);
                }
            }
        } else if (body.hasCryptoDeleteClaim()) {
            if (body.getCryptoDeleteClaim().hasAccountIDToDeleteFrom()) {
                entityId = getEntityId(body.getCryptoDeleteClaim().getAccountIDToDeleteFrom());
            }
        } else if (body.hasCryptoUpdateAccount()) {
            CryptoUpdateTransactionBody txMessage = body.getCryptoUpdateAccount();
            entity = getEntity(txMessage.getAccountIDToUpdate());
            if (doUpdateEntity) {
                proxyEntityId = getEntityId(txMessage.getProxyAccountID());

                if (txMessage.hasExpirationTime()) {
                    entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
                }

                if (txMessage.hasAutoRenewPeriod()) {
                    entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
                }

                if (txMessage.hasKey()) {
                    entity.setKey(txMessage.getKey().toByteArray());
                }
            }
        } else if (body.hasFileCreate()) {
            if (txRecord.getReceipt().hasFileID()) { // Implies SUCCESS
                FileCreateTransactionBody txMessage = body.getFileCreate();
                entity = getEntity(txRecord.getReceipt().getFileID());

                if (txMessage.hasExpirationTime()) {
                    entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
                }

                if (txMessage.hasKeys()) {
                    entity.setKey(txMessage.getKeys().toByteArray());
                }
            }
        } else if (body.hasFileAppend()) {
            if (body.getFileAppend().hasFileID()) {
                entityId = getEntityId(body.getFileAppend().getFileID());
            }
        } else if (body.hasFileDelete()) {
            if (body.getFileDelete().hasFileID()) {
                entity = getEntity(body.getFileDelete().getFileID());
                if (doUpdateEntity) {
                    entity.setDeleted(true);
                }
            }
        } else if (body.hasFileUpdate()) {
            FileUpdateTransactionBody txMessage = body.getFileUpdate();
            entity = getEntity(txMessage.getFileID());

            if (doUpdateEntity) {
                if (txMessage.hasExpirationTime()) {
                    entity.setExpiryTimeNs(Utility.timestampInNanosMax(txMessage.getExpirationTime()));
                }

                if (txMessage.hasKeys()) {
                    entity.setKey(txMessage.getKeys().toByteArray());
                }
            }
        } else if (body.hasSystemDelete()) {
            if (body.getSystemDelete().hasContractID()) {
                entity = getEntity(body.getSystemDelete().getContractID());
                if (doUpdateEntity) {
                    entity.setDeleted(true);
                }
            } else if (body.getSystemDelete().hasFileID()) {
                entity = getEntity(body.getSystemDelete().getFileID());
                if (doUpdateEntity) {
                    entity.setDeleted(true);
                }
            }
        } else if (body.hasSystemUndelete()) {
            if (body.getSystemUndelete().hasContractID()) {
                entity = getEntity(body.getSystemUndelete().getContractID());
                if (doUpdateEntity) {
                    entity.setDeleted(false);
                }
            } else if (body.getSystemUndelete().hasFileID()) {
                entity = getEntity(body.getSystemUndelete().getFileID());
                if (doUpdateEntity) {
                    entity.setDeleted(false);
                }
            }
        } else if (body.hasConsensusCreateTopic()) {
            entity = storeConsensusCreateTopic(body, txRecord);
        } else if (body.hasConsensusUpdateTopic()) {
            entity = storeConsensusUpdateTopic(body, txRecord);
        } else if (body.hasConsensusDeleteTopic()) {
            entity = storeConsensusDeleteTopic(body, txRecord);
        } else if (body.hasConsensusSubmitMessage()) {
            entityId = storeConsensusSubmitMessage(body, txRecord);
        }

        TransactionID transactionID = body.getTransactionID();
        long validDurationSeconds = body.hasTransactionValidDuration() ? body.getTransactionValidDuration()
                .getSeconds() : null;
        long validStartNs = Utility.timeStampInNanos(transactionID.getTransactionValidStart());
        long consensusNs = Utility.timeStampInNanos(txRecord.getConsensusTimestamp());
        AccountID payerAccountId = transactionID.getAccountID();

        com.hedera.mirror.importer.domain.Transaction tx = new com.hedera.mirror.importer.domain.Transaction();
        tx.setChargedTxFee(txRecord.getTransactionFee());
        tx.setConsensusNs(consensusNs);
        if (entityId != null) {
            var tempEntity = new Entities();
            tempEntity.setId(entityId.getId());
            tempEntity.setEntityShard(entityId.getEntityShard());
            tempEntity.setEntityRealm(entityId.getEntityRealm());
            tempEntity.setEntityNum(entityId.getEntityNum());
            tx.setEntity(tempEntity);
        } else if (null != entity) {
            tx.setEntity(entity);
        }
        tx.setInitialBalance(initialBalance);
        tx.setMemo(body.getMemo().getBytes());
        tx.setMaxFee(body.getTransactionFee());
        tx.setRecordFileId(fileId);
        tx.setResult(txRecord.getReceipt().getStatusValue());
        tx.setType(getTransactionType(body));
        tx.setTransactionBytes(parserProperties.isPersistTransactionBytes() ? rawBytes : null);
        tx.setTransactionHash(txRecord.getTransactionHash().toByteArray());
        tx.setValidDurationSeconds(validDurationSeconds);
        tx.setValidStartNs(validStartNs);

        if (!transactionFilter.test(tx)) {
            log.debug("Ignoring transaction {}", tx);
            return;
        }

        if (entity != null) {
            if (proxyEntityId != null) {
                entity.setProxyAccountId(proxyEntityId.getId());
            }
            entity.setAutoRenewAccount(createEntity(entity.getAutoRenewAccount()));
            entity = entityRepository.save(entity);
            sqlInsertTransaction.setLong(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), entity.getId());
        } else if (entityId != null) {
            sqlInsertTransaction.setObject(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), entityId.getId());
        } else {
            sqlInsertTransaction.setObject(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), null);
        }

        EntityId payerEntityId = getEntityId(payerAccountId);
        EntityId nodeEntityId = getEntityId(body.getNodeAccountID());
        tx.setNodeAccountId(nodeEntityId.getId());
        tx.setPayerAccountId(payerEntityId.getId());

        // Temporary until we convert SQL statements to repository invocations
        sqlInsertTransaction.setLong(F_TRANSACTION.FK_NODE_ACCOUNT_ID.ordinal(), tx.getNodeAccountId());
        sqlInsertTransaction.setBytes(F_TRANSACTION.MEMO.ordinal(), tx.getMemo());
        sqlInsertTransaction.setLong(F_TRANSACTION.VALID_START_NS.ordinal(), tx.getValidStartNs());
        sqlInsertTransaction.setInt(F_TRANSACTION.TYPE.ordinal(), tx.getType());
        sqlInsertTransaction.setLong(F_TRANSACTION.FK_REC_FILE_ID.ordinal(), tx.getRecordFileId());
        sqlInsertTransaction.setLong(F_TRANSACTION.VALID_DURATION_SECONDS.ordinal(), tx.getValidDurationSeconds());
        sqlInsertTransaction.setLong(F_TRANSACTION.FK_PAYER_ACCOUNT_ID.ordinal(), tx.getPayerAccountId());
        sqlInsertTransaction.setLong(F_TRANSACTION.RESULT.ordinal(), tx.getResult());
        sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_NS.ordinal(), tx.getConsensusNs());
        sqlInsertTransaction.setLong(F_TRANSACTION.CHARGED_TX_FEE.ordinal(), tx.getChargedTxFee());
        sqlInsertTransaction.setLong(F_TRANSACTION.MAX_FEE.ordinal(), tx.getMaxFee());
        sqlInsertTransaction.setBytes(F_TRANSACTION.TRANSACTION_HASH.ordinal(), tx.getTransactionHash());
        sqlInsertTransaction.setBytes(F_TRANSACTION.TRANSACTION_BYTES.ordinal(), tx.getTransactionBytes());
        sqlInsertTransaction.setLong(F_TRANSACTION.INITIAL_BALANCE.ordinal(), tx.getInitialBalance());
        sqlInsertTransaction.addBatch();
        log.debug("Storing transaction: {}", tx);

        if ((txRecord.hasTransferList()) && parserProperties.isPersistCryptoTransferAmounts()) {
            processNonFeeTransfers(consensusNs, payerAccountId, body, txRecord);
            if (body.hasCryptoCreateAccount() && isSuccessful(txRecord)) {
                insertCryptoCreateTransferList(consensusNs, txRecord, body, txRecord.getReceipt()
                        .getAccountID(), payerAccountId);
            } else {
                insertTransferList(consensusNs, txRecord.getTransferList());
            }
        }

        // TransactionBody-specific handlers.
        // If so-configured, each will update the SQL prepared statements via addBatch().
        if (body.hasContractCall()) {
            insertContractCall(consensusNs, body.getContractCall(), txRecord);
        } else if (body.hasContractCreateInstance()) {
            insertContractCreateInstance(consensusNs, body.getContractCreateInstance(), txRecord);
        }
        if (doUpdateEntity) {
            if (body.hasConsensusSubmitMessage()) {
                insertConsensusTopicMessage(body.getConsensusSubmitMessage(), txRecord);
            } else if (body.hasCryptoAddClaim()) {
                insertCryptoAddClaim(consensusNs, body.getCryptoAddClaim());
            } else if (body.hasFileAppend()) {
                insertFileAppend(consensusNs, body.getFileAppend());
            } else if (body.hasFileCreate()) {
                insertFileCreate(consensusNs, body.getFileCreate(), txRecord);
            } else if (body.hasFileUpdate()) {
                insertFileUpdate(consensusNs, body.getFileUpdate());
            }
        }

        if (batch_count == BATCH_SIZE - 1) {
            // execute any remaining batches
            executeBatches();
            batch_count = 0;
        } else {
            batch_count += 1;
        }
    }

    /**
     * Should the given transaction/record generate non_fee_transfers based on what type the transaction is,
     * it's status, and run-time configuration concerning which situations warrant storing.
     * @param body
     * @param transactionRecord
     * @return
     */
    private static boolean shouldStoreNonFeeTransfers(TransactionBody body, TransactionRecord transactionRecord) {
        if (!body.hasCryptoCreateAccount() && !body.hasContractCreateInstance() && !body.hasCryptoTransfer() && !body
                .hasContractCall()) {
            return false;
        }
        return parserProperties.isPersistNonFeeTransfers();
    }

    /**
     * Additionally store rows in the non_fee_transactions table if applicable. This will allow the rest-api to create
     * an itemized set of transfers that reflects non-fees (explicit transfers), threshold records, node fee, and
     * network+service fee (paid to treasury).
     */
    private static void processNonFeeTransfers(long consensusTimestamp, AccountID payerAccountId,
                                               TransactionBody body, TransactionRecord transactionRecord)
            throws SQLException {
        if (!shouldStoreNonFeeTransfers(body, transactionRecord)) {
            return;
        }

        for (var aa : nonFeeTransfersExtractor.extractNonFeeTransfers(payerAccountId, body, transactionRecord)) {
            addNonFeeTransferInserts(consensusTimestamp, aa.getAccountID().getRealmNum(),
                    aa.getAccountID().getAccountNum(), aa.getAmount());
        }
    }

    private static void addNonFeeTransferInserts(long consensusTimestamp, long realm, long accountNum, long amount) {
        if (0 != amount) {
            postgresWriter.onNonFeeTransfer(
                    new NonFeeTransfer(consensusTimestamp, realm, accountNum, amount));
        }
    }

    /**
     * Store ConsensusCreateTopic transaction in the database.
     *
     * @param body
     * @param transactionRecord
     * @return Entity ID of the newly created topic, or 0 if no topic was created
     * @throws SQLException
     * @throws IllegalArgumentException
     */
    private static Entities storeConsensusCreateTopic(TransactionBody body,
                                                      TransactionRecord transactionRecord) {
        if (!body.hasConsensusCreateTopic()) {
            throw new IllegalArgumentException("transaction is not a ConsensusCreateTopic");
        }

        if (!transactionRecord.getReceipt().hasTopicID()) {
            return null;
        }

        Entities entity = getEntity(transactionRecord.getReceipt().getTopicID());
        var transactionBody = body.getConsensusCreateTopic();

        if (transactionBody.hasAutoRenewAccount()) {
            Entities autoRenewAccount = getEntity(transactionBody.getAutoRenewAccount());
            entity.setAutoRenewAccount(autoRenewAccount);
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        // If either key is empty, they should end up as empty bytea in the DB to indicate that there is
        // explicitly no value, as opposed to null which has been used to indicate the value is unknown.
        var adminKey = transactionBody.hasAdminKey() ? transactionBody.getAdminKey().toByteArray() : new byte[0];
        var submitKey = transactionBody.hasSubmitKey() ? transactionBody.getSubmitKey().toByteArray() : new byte[0];

        entity.setMemo(transactionBody.getMemo());
        entity.setKey(adminKey);
        entity.setSubmitKey(submitKey);
        return entity;
    }

    /**
     * Store ConsensusUpdateTopic transaction in the database.
     *
     * @param body
     * @param transactionRecord
     * @return Entity ID of the updated topic, or 0 if no topic was updated
     * @throws SQLException
     * @throws IllegalArgumentException
     */
    private static Entities storeConsensusUpdateTopic(TransactionBody body,
                                                      TransactionRecord transactionRecord) {
        if (!body.hasConsensusUpdateTopic()) {
            throw new IllegalArgumentException("transaction is not a ConsensusUpdateTopic");
        }

        var transactionBody = body.getConsensusUpdateTopic();
        if (!transactionBody.hasTopicID()) {
            log.warn("Encountered a ConsensusUpdateTopic transaction without topic ID: {}", body);
            return null;
        }

        Entities entity = getEntity(transactionBody.getTopicID());

        if (isSuccessful(transactionRecord)) {
            if (transactionBody.hasExpirationTime()) {
                Timestamp expirationTime = transactionBody.getExpirationTime();
                entity.setExpiryTimeNs(Utility.timestampInNanosMax(expirationTime));
            }

            if (transactionBody.hasAutoRenewAccount()) {
                Entities autoRenewAccount = getEntity(transactionBody.getAutoRenewAccount());
                entity.setAutoRenewAccount(autoRenewAccount);
            }

            if (transactionBody.hasAutoRenewPeriod()) {
                entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
            }

            if (transactionBody.hasAdminKey()) {
                entity.setKey(transactionBody.getAdminKey().toByteArray());
            }

            if (transactionBody.hasSubmitKey()) {
                entity.setSubmitKey(transactionBody.getSubmitKey().toByteArray());
            }

            if (transactionBody.hasMemo()) {
                entity.setMemo(transactionBody.getMemo().getValue());
            }
        }

        return entity;
    }

    /**
     * Store ConsensusDeleteTopic transaction in the database.
     *
     * @param body
     * @param transactionRecord
     * @return Entity ID of the deleted topic, or 0 if no topic was deleted
     * @throws SQLException
     * @throws IllegalArgumentException
     */
    private static Entities storeConsensusDeleteTopic(TransactionBody body,
                                                      TransactionRecord transactionRecord) {
        if (!body.hasConsensusDeleteTopic()) {
            throw new IllegalArgumentException("transaction is not a ConsensusDeleteTopic");
        }

        var transactionBody = body.getConsensusDeleteTopic();
        if (!transactionBody.hasTopicID()) {
            log.warn("Encountered a ConsensusDeleteTopic transaction without topic ID: {}", body);
            return null;
        }

        Entities entity = getEntity(transactionBody.getTopicID());
        if (isSuccessful(transactionRecord)) {
            entity.setDeleted(true);
        }

        return entity;
    }

    /**
     * Store ConsensusSubmitMessage transaction in the database.
     *
     * @param body
     * @param transactionRecord
     * @return Entity ID of the topic, or 0 if no topic was deleted
     * @throws SQLException
     * @throws IllegalArgumentException
     */
    private static EntityId storeConsensusSubmitMessage(TransactionBody body,
                                                        TransactionRecord transactionRecord) {
        if (!body.hasConsensusSubmitMessage()) {
            throw new IllegalArgumentException("transaction is not a ConsensusSubmitMessage");
        }

        var transactionBody = body.getConsensusSubmitMessage();
        if (!transactionBody.hasTopicID()) {
            log.warn("Encountered a ConsensusSubmitMessage transaction without topic ID: {}", body);
            return null;
        }

        return getEntityId(transactionBody.getTopicID());
    }

    private static void insertConsensusTopicMessage(ConsensusSubmitMessageTransactionBody transactionBody,
                                                    TransactionRecord transactionRecord) throws SQLException {
        var receipt = transactionRecord.getReceipt();
        var ts = transactionRecord.getConsensusTimestamp();
        var topicId = transactionBody.getTopicID();
        sqlInsertTopicMessage.setLong(1, Utility.timeStampInNanos(ts));
        sqlInsertTopicMessage.setShort(2, (short) topicId.getRealmNum());
        sqlInsertTopicMessage.setInt(3, (int) topicId.getTopicNum());
        sqlInsertTopicMessage.setBytes(4, transactionBody.getMessage().toByteArray());
        sqlInsertTopicMessage.setBytes(5, receipt.getTopicRunningHash().toByteArray());
        sqlInsertTopicMessage.setLong(6, receipt.getTopicSequenceNumber());
        sqlInsertTopicMessage.addBatch();
    }

    private static void insertFileCreate(long consensusTimestamp, FileCreateTransactionBody transactionBody,
                                         TransactionRecord transactionRecord) throws SQLException {
        if (parserProperties.isPersistFiles() ||
                (parserProperties.isPersistSystemFiles() && transactionRecord.getReceipt().getFileID()
                        .getFileNum() < 1000)) {
            byte[] contents = transactionBody.getContents().toByteArray();
            sqlInsertFileData.setLong(F_FILE_DATA.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
            sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
            sqlInsertFileData.addBatch();
        }
    }

    private static void insertFileAppend(long consensusTimestamp, FileAppendTransactionBody transactionBody)
            throws SQLException, IOException {
        if (parserProperties.isPersistFiles() ||
                (parserProperties.isPersistSystemFiles() && transactionBody.getFileID().getFileNum() < 1000)) {
            byte[] contents = transactionBody.getContents().toByteArray();
            sqlInsertFileData.setLong(F_FILE_DATA.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
            sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
            sqlInsertFileData.addBatch();

            // update the local address book
            if (isFileAddressBook(transactionBody.getFileID())) {
                // we have an address book update, refresh the local file
                networkAddressBook.append(contents);
            }
        }
    }

    private static void insertCryptoAddClaim(long consensusTimestamp,
                                             CryptoAddClaimTransactionBody transactionBody) throws SQLException {
        if (parserProperties.isPersistClaims()) {
            byte[] claim = transactionBody.getClaim().getHash().toByteArray();

            sqlInsertClaimData.setLong(F_LIVEHASH_DATA.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
            sqlInsertClaimData.setBytes(F_LIVEHASH_DATA.LIVEHASH.ordinal(), claim);
            sqlInsertClaimData.addBatch();
        }
    }

    private static void insertContractCall(long consensusTimestamp,
                                           ContractCallTransactionBody transactionBody,
                                           TransactionRecord transactionRecord) throws SQLException {
        if (parserProperties.isPersistContracts()) {
            byte[] functionParams = transactionBody.getFunctionParameters().toByteArray();
            long gasSupplied = transactionBody.getGas();
            byte[] callResult = new byte[0];
            long gasUsed = 0;
            if (transactionRecord.hasContractCallResult()) {
                callResult = transactionRecord.getContractCallResult().toByteArray();
                gasUsed = transactionRecord.getContractCallResult().getGasUsed();
            }

            insertContractResults(sqlInsertContractCall, consensusTimestamp, functionParams, gasSupplied, callResult,
                    gasUsed);
        }
    }

    private static void insertContractCreateInstance(long consensusTimestamp,
                                                     ContractCreateTransactionBody transactionBody,
                                                     TransactionRecord transactionRecord) throws SQLException {
        if (parserProperties.isPersistContracts()) {
            byte[] functionParams = transactionBody.getConstructorParameters().toByteArray();
            long gasSupplied = transactionBody.getGas();
            byte[] callResult = new byte[0];
            long gasUsed = 0;
            if (transactionRecord.hasContractCreateResult()) {
                callResult = transactionRecord.getContractCreateResult().toByteArray();
                gasUsed = transactionRecord.getContractCreateResult().getGasUsed();
            }

            insertContractResults(sqlInsertContractCall, consensusTimestamp, functionParams, gasSupplied, callResult,
                    gasUsed);
        }
    }

    private static void insertTransferList(long consensusTimestamp, TransferList transferList) {
        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            var accountId = aa.getAccountID();
            createEntity(getEntity(aa.getAccountID()));
            addCryptoTransferList(consensusTimestamp, accountId.getRealmNum(), accountId.getAccountNum(), aa
                    .getAmount());
        }
    }

    private static void insertCryptoCreateTransferList(long consensusTimestamp,
                                                       TransactionRecord txRecord,
                                                       TransactionBody body,
                                                       AccountID createdAccountId,
                                                       AccountID payerAccountId)
            throws SQLException {

        long initialBalance = 0;
        long createdAccountNum = 0;

        // no need to add missing initial balance to transfer list if this is realm and shard <> 0
        boolean addInitialBalance = (txRecord.getReceipt().getAccountID().getShardNum() == 0) && (txRecord.getReceipt()
                .getAccountID().getRealmNum() == 0);

        if (addInitialBalance) {
            initialBalance = body.getCryptoCreateAccount().getInitialBalance();
            createdAccountNum = txRecord.getReceipt().getAccountID().getAccountNum();
        }
        TransferList transferList = txRecord.getTransferList();
        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            var aa = transferList.getAccountAmounts(i);
            long amount = aa.getAmount();
            var accountId = aa.getAccountID();
            long accountNum = accountId.getAccountNum();
            createEntity(getEntity(accountId));
            addCryptoTransferList(consensusTimestamp, accountId.getRealmNum(), accountNum, aa.getAmount());

            if (addInitialBalance && (initialBalance == aa.getAmount()) && (accountNum == createdAccountNum)) {
                addInitialBalance = false;
            }
        }

        if (addInitialBalance) {
            createEntity(getEntity(payerAccountId));
            addCryptoTransferList(consensusTimestamp, payerAccountId.getRealmNum(), payerAccountId
                    .getAccountNum(), -initialBalance);

            createEntity(getEntity(createdAccountId));
            addCryptoTransferList(consensusTimestamp, createdAccountId
                    .getRealmNum(), createdAccountNum, initialBalance);
        }
    }

    private static void addCryptoTransferList(long consensusTimestamp, long realmNum, long accountNum, long amount) {
        postgresWriter.onCryptoTransferList(new CryptoTransfer(consensusTimestamp, amount, realmNum, accountNum));
    }

    private static boolean isFileAddressBook(FileID fileId) {
        return (fileId.getFileNum() == 102) && (fileId.getShardNum() == 0) && (fileId.getRealmNum() == 0);
    }

    private static void insertFileUpdate(long consensusTimestamp, FileUpdateTransactionBody transactionBody)
            throws SQLException, IOException {
        FileID fileId = transactionBody.getFileID();
        if (parserProperties.isPersistFiles() ||
                (parserProperties.isPersistSystemFiles() && fileId.getFileNum() < 1000)) {
            byte[] contents = transactionBody.getContents().toByteArray();
            sqlInsertFileData.setLong(F_FILE_DATA.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
            sqlInsertFileData.setBytes(F_FILE_DATA.FILE_DATA.ordinal(), contents);
            sqlInsertFileData.addBatch();
        }

        // update the local address book
        if (isFileAddressBook(fileId)) {
            // we have an address book update, refresh the local file
            networkAddressBook.update(transactionBody.getContents().toByteArray());
        }
    }

    /**
     * Because body.getDataCase() can return null for unknown transaction types, we instead get oneof generically
     *
     * @param body
     * @return The protobuf ID that represents the transaction type
     */
    private static int getTransactionType(TransactionBody body) {
        TransactionBody.DataCase dataCase = body.getDataCase();

        if (dataCase == null || dataCase == TransactionBody.DataCase.DATA_NOT_SET) {
            Set<Integer> unknownFields = body.getUnknownFields().asMap().keySet();

            if (unknownFields.size() != 1) {
                throw new IllegalStateException("Unable to guess correct transaction type since there's not exactly " +
                        "one: " + unknownFields);
            }

            int transactionType = unknownFields.iterator().next();
            log.warn("Encountered unknown transaction type: {}", transactionType);
            return transactionType;
        }

        return dataCase.getNumber();
    }

    public static void insertContractResults(PreparedStatement insert, long consensusTimestamp,
                                             byte[] functionParams, long gasSupplied,
                                             byte[] callResult, long gasUsed) throws SQLException {
        insert.setLong(F_CONTRACT_CALL.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
        insert.setBytes(F_CONTRACT_CALL.FUNCTION_PARAMS.ordinal(), functionParams);
        insert.setLong(F_CONTRACT_CALL.GAS_SUPPLIED.ordinal(), gasSupplied);
        insert.setBytes(F_CONTRACT_CALL.CALL_RESULT.ordinal(), callResult);
        insert.setLong(F_CONTRACT_CALL.GAS_USED.ordinal(), gasUsed);

        insert.addBatch();
    }

    private static void executeBatches() throws SQLException {
        int[] transactions = sqlInsertTransaction.executeBatch();
        int[] fileData = sqlInsertFileData.executeBatch();
        int[] contractCalls = sqlInsertContractCall.executeBatch();
        int[] claimData = sqlInsertClaimData.executeBatch();
        int[] topicMessages = sqlInsertTopicMessage.executeBatch();
        postgresWriter.executeBatches();
        log.info("Inserted {} transactions, {} files, {} contracts, {} claims, {} topic messages",
                transactions.length, fileData.length, contractCalls.length, claimData.length, topicMessages.length);
    }

    public static Entities getEntity(AccountID accountID) {
        return getEntity(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum(), "account");
    }

    public static Entities getEntity(ContractID cid) {
        return getEntity(cid.getShardNum(), cid.getRealmNum(), cid.getContractNum(), "contract");
    }

    public static Entities getEntity(FileID fileId) {
        return getEntity(fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum(), "file");
    }

    public static Entities getEntity(TopicID topicId) {
        return getEntity(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum(), "topic");
    }

    private static Entities getEntity(long shardNum, long realmNum, long entityNum, String type) {
        return entityRepository.findByPrimaryKey(shardNum, realmNum, entityNum).orElseGet(() -> {
            Entities entity = new Entities();
            entity.setEntityNum(entityNum);
            entity.setEntityRealm(realmNum);
            entity.setEntityShard(shardNum);
            entity.setEntityTypeId(entityTypeRepository.findByName(type).map(EntityType::getId).get());
            return entity;
        });
    }

    public static EntityId getEntityId(AccountID accountID) {
        return getEntityId(accountID.getShardNum(), accountID.getRealmNum(), accountID.getAccountNum(), "account");
    }

    public static EntityId getEntityId(ContractID cid) {
        return getEntityId(cid.getShardNum(), cid.getRealmNum(), cid.getContractNum(), "contract");
    }

    public static EntityId getEntityId(FileID fileId) {
        return getEntityId(fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum(), "file");
    }

    public static EntityId getEntityId(TopicID topicId) {
        return getEntityId(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum(), "topic");
    }

    private static EntityId getEntityId(long shardNum, long realmNum, long entityNum, String type) {
        if (0 == entityNum) {
            return null;
        }
        return entityRepository.findEntityIdByNativeIds(shardNum, realmNum, entityNum).orElseGet(() -> {
            Entities entityId = new Entities();
            entityId.setEntityShard(shardNum);
            entityId.setEntityRealm(realmNum);
            entityId.setEntityNum(entityNum);
            entityId.setEntityTypeId(entityTypeRepository.findByName(type).map(EntityType::getId).get());
            return entityRepository.saveAndCacheEntityId(entityId);
        });
    }

    private static Entities createEntity(Entities entity) {
        if (entity != null && entity.getId() == null) {
            log.debug("Creating entity: {}", () -> entity.getDisplayId());
            var result = entityRepository.save(entity);
            var entityId = new EntityId(result.getId(), result.getEntityShard(), result.getEntityRealm(),
                    result.getEntityNum(), result.getEntityTypeId());
            entityRepository.cache(entityId);
            return result;
        }
        return entity;
    }

    public enum INIT_RESULT {
        OK, FAIL, SKIP
    }

    enum F_TRANSACTION {
        ZERO // column indices start at 1, this creates the necessary offset
        , FK_NODE_ACCOUNT_ID, MEMO, VALID_START_NS, TYPE, FK_PAYER_ACCOUNT_ID, RESULT, CONSENSUS_NS,
        CUD_ENTITY_ID, CHARGED_TX_FEE, INITIAL_BALANCE, FK_REC_FILE_ID, VALID_DURATION_SECONDS, MAX_FEE,
        TRANSACTION_HASH, TRANSACTION_BYTES
    }

    enum F_FILE_DATA {
        ZERO, CONSENSUS_TIMESTAMP, FILE_DATA
    }

    enum F_CONTRACT_CALL {
        ZERO, CONSENSUS_TIMESTAMP, FUNCTION_PARAMS, GAS_SUPPLIED, CALL_RESULT, GAS_USED
    }

    enum F_LIVEHASH_DATA {
        ZERO, CONSENSUS_TIMESTAMP, LIVEHASH
    }
}
