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
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddClaimTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
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
import javax.inject.Named;

import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.addressbook.NetworkAddressBook;
import com.hedera.mirror.importer.util.DatabaseUtilities;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
public class RecordFileLogger {
    public static Connection connect = null;
    private static Entities entities = null;
    private static RecordParserProperties parserProperties = null;
    private static NetworkAddressBook networkAddressBook = null;

    private static long fileId = 0;
    private static long BATCH_SIZE = 100;
    private static long batch_count = 0;

    private static PreparedStatement sqlInsertTransaction;
    private static PreparedStatement sqlInsertTransferList;
    private static PreparedStatement sqlInsertFileData;
    private static PreparedStatement sqlInsertContractCall;
    private static PreparedStatement sqlInsertClaimData;

    public RecordFileLogger(RecordParserProperties parserProperties, NetworkAddressBook networkAddressBook) {
        RecordFileLogger.parserProperties = parserProperties;
        RecordFileLogger.networkAddressBook = networkAddressBook;
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
            entities = new Entities(connect);
        } catch (SQLException e) {
            log.error("Unable to fetch entity types", e);
            return false;
        }

        try {
            sqlInsertTransaction = connect.prepareStatement("INSERT INTO t_transactions"
                    + " (fk_node_acc_id, memo, valid_start_ns, type, fk_payer_acc_id"
                    + ", result, consensus_ns, fk_cud_entity_id, charged_tx_fee"
                    + ", initial_balance, fk_rec_file_id, valid_duration_seconds, max_fee"
                    + ", transaction_hash, transaction_bytes)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            sqlInsertTransferList = connect.prepareStatement("INSERT INTO t_cryptotransferlists"
                    + " (consensus_timestamp, amount, realm_num, entity_num)"
                    + " VALUES (?, ?, ?, ?)");

            sqlInsertFileData = connect.prepareStatement("INSERT INTO t_file_data"
                    + " (consensus_timestamp, file_data)"
                    + " VALUES (?, ?)");

            sqlInsertContractCall = connect.prepareStatement("INSERT INTO t_contract_result"
                    + " (consensus_timestamp, function_params, gas_supplied, call_result, gas_used)"
                    + " VALUES (?, ?, ?, ?, ?)");

            sqlInsertClaimData = connect.prepareStatement("INSERT INTO t_livehashes"
                    + " (consensus_timestamp, livehash)"
                    + " VALUES (?, ?)");
        } catch (SQLException e) {
            log.error("Unable to prepare SQL statements", e);
            return false;
        }

        return true;
    }

    public static boolean finish() {
        try {
            sqlInsertFileData.close();
            sqlInsertTransferList.close();
            sqlInsertTransaction.close();
            sqlInsertContractCall.close();
            sqlInsertClaimData.close();

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
            log.error("Error saving file {} in database", fileName, e);
        }
        return INIT_RESULT.FAIL;
    }

    public static void completeFile(String fileHash, String previousHash) throws SQLException {
        try (CallableStatement fileClose = connect.prepareCall("{call f_file_complete( ?, ?, ? ) }")) {
            // execute any remaining batches
            executeBatches();

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
        long createdAccountId = 0;
        TransactionBody body;

        if (transaction.hasBody()) {
            body = transaction.getBody();
        } else {
            body = TransactionBody.parseFrom(transaction.getBodyBytes());
        }
        long fkNodeAccountId = entities.createOrGetEntity(body.getNodeAccountID());
        TransactionID transactionID = body.getTransactionID();
        long validDurationSeconds = body.hasTransactionValidDuration() ? body.getTransactionValidDuration()
                .getSeconds() : null;

        var vs = transactionID.getTransactionValidStart();
        long validStartNs = Utility.convertToNanos(vs.getSeconds(), vs.getNanos());
        var c = txRecord.getConsensusTimestamp();
        long consensusNs = Utility.convertToNanos(c.getSeconds(), c.getNanos());

        sqlInsertTransaction.setLong(F_TRANSACTION.FK_NODE_ACCOUNT_ID.ordinal(), fkNodeAccountId);
        sqlInsertTransaction.setBytes(F_TRANSACTION.MEMO.ordinal(), body.getMemo().getBytes());
        sqlInsertTransaction.setLong(F_TRANSACTION.VALID_START_NS.ordinal(), validStartNs);
        sqlInsertTransaction.setInt(F_TRANSACTION.TYPE.ordinal(), getTransactionType(body));
        sqlInsertTransaction.setLong(F_TRANSACTION.FK_REC_FILE_ID.ordinal(), fileId);
        sqlInsertTransaction.setLong(F_TRANSACTION.VALID_DURATION_SECONDS.ordinal(), validDurationSeconds);

        AccountID fkPayerAccountId = transactionID.getAccountID();

        sqlInsertTransaction
                .setLong(F_TRANSACTION.FK_PAYER_ACCOUNT_ID.ordinal(), entities.createOrGetEntity(fkPayerAccountId));
        sqlInsertTransaction.setLong(F_TRANSACTION.RESULT.ordinal(), txRecord.getReceipt().getStatusValue());
        sqlInsertTransaction.setLong(F_TRANSACTION.CONSENSUS_NS.ordinal(), consensusNs);
        sqlInsertTransaction.setLong(F_TRANSACTION.CHARGED_TX_FEE.ordinal(), txRecord.getTransactionFee());
        sqlInsertTransaction.setLong(F_TRANSACTION.MAX_FEE.ordinal(), body.getTransactionFee());
        sqlInsertTransaction
                .setBytes(F_TRANSACTION.TRANSACTION_HASH.ordinal(), txRecord.getTransactionHash().toByteArray());
        // Store the raw bytes only if configured - otherwise null
        sqlInsertTransaction
                .setBytes(F_TRANSACTION.TRANSACTION_BYTES.ordinal(),
                        parserProperties.isPersistTransactionBytes() ? rawBytes : null);

        long entityId = 0;
        long initialBalance = 0;

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
                entityId = entities.createOrGetEntity(body.getContractCall().getContractID());
            }
        } else if (body.hasContractCreateInstance()) {
            if (txRecord.getReceipt().hasContractID()) { // implies SUCCESS
                var contractId = txRecord.getReceipt().getContractID();
                ContractCreateTransactionBody txMessage = body.getContractCreateInstance();
                long expiration_time_sec = 0;
                long expiration_time_nanos = 0;
                long auto_renew_period = 0;
                if (txMessage.hasAutoRenewPeriod()) {
                    auto_renew_period = txMessage.getAutoRenewPeriod().getSeconds();
                }
                byte[] key = null;
                if (txMessage.hasAdminKey()) {
                    key = txMessage.getAdminKey().toByteArray();
                }
                long proxy_account_id = entities.createOrGetEntity(txMessage.getProxyAccountID());

                entityId = entities
                        .createEntity(contractId, expiration_time_sec, expiration_time_nanos, auto_renew_period, key,
                                proxy_account_id);
            }

            initialBalance = body.getContractCreateInstance().getInitialBalance();
        } else if (body.hasContractDeleteInstance()) {
            if (body.getContractDeleteInstance().hasContractID()) {
                var contractId = body.getContractDeleteInstance().getContractID();
                if (doUpdateEntity) {
                    entityId = entities.deleteEntity(contractId);
                } else {
                    entityId = entities.createOrGetEntity(contractId);
                }
            }
        } else if (body.hasContractUpdateInstance()) {
            ContractUpdateTransactionBody txMessage = body.getContractUpdateInstance();
            var contractId = txMessage.getContractID();
            long expiration_time_sec = 0;
            long expiration_time_nanos = 0;
            if (txMessage.hasExpirationTime()) {
                expiration_time_sec = txMessage.getExpirationTime().getSeconds();
                expiration_time_nanos = txMessage.getExpirationTime().getNanos();
            }
            long auto_renew_period = 0;
            if (txMessage.hasAutoRenewPeriod()) {
                auto_renew_period = txMessage.getAutoRenewPeriod().getSeconds();
            }

            byte[] key = null;
            if (txMessage.hasAdminKey()) {
                key = txMessage.getAdminKey().toByteArray();
            }

            if (doUpdateEntity) {
                long proxy_account_id = entities.createOrGetEntity(txMessage.getProxyAccountID());
                entityId = entities.updateEntity(contractId, expiration_time_sec,
                        expiration_time_nanos, auto_renew_period, key, proxy_account_id);
            } else {
                entityId = entities.createOrGetEntity(contractId);
            }
        } else if (body.hasCryptoAddClaim()) {
            if (body.getCryptoAddClaim().hasClaim()) {
                if (body.getCryptoAddClaim().getClaim().hasAccountID()) {
                    entityId = entities.createOrGetEntity(body.getCryptoAddClaim().getClaim().getAccountID());
                }
            }
        } else if (body.hasCryptoCreateAccount()) {
            if (txRecord.getReceipt().hasAccountID()) { // Implies SUCCESS
                CryptoCreateTransactionBody txMessage = body.getCryptoCreateAccount();
                long expiration_time_sec = 0;
                long expiration_time_nanos = 0;
                long auto_renew_period = 0;
                if (txMessage.hasAutoRenewPeriod()) {
                    auto_renew_period = txMessage.getAutoRenewPeriod().getSeconds();
                }
                byte[] key = null;
                if (txMessage.hasKey()) {
                    key = txMessage.getKey().toByteArray();
                }
                long proxy_account_id = entities.createOrGetEntity(txMessage.getProxyAccountID());

                entityId = entities.createEntity(txRecord.getReceipt()
                                .getAccountID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, key,
                        proxy_account_id);
            }

            initialBalance = body.getCryptoCreateAccount().getInitialBalance();
        } else if (body.hasCryptoDelete()) {
            if (body.getCryptoDelete().hasDeleteAccountID()) {
                var accountId = body.getCryptoDelete().getDeleteAccountID();
                if (doUpdateEntity) {
                    entityId = entities.deleteEntity(accountId);
                } else {
                    entityId = entities.createOrGetEntity(accountId);
                }
            }
        } else if (body.hasCryptoDeleteClaim()) {
            if (body.getCryptoDeleteClaim().hasAccountIDToDeleteFrom()) {
                entityId = entities.createOrGetEntity(body.getCryptoDeleteClaim().getAccountIDToDeleteFrom());
            }
        } else if (body.hasCryptoUpdateAccount()) {
            CryptoUpdateTransactionBody txMessage = body.getCryptoUpdateAccount();
            var accountId = txMessage.getAccountIDToUpdate();
            long expiration_time_sec = 0;
            long expiration_time_nanos = 0;
            if (txMessage.hasExpirationTime()) {
                expiration_time_sec = txMessage.getExpirationTime().getSeconds();
                expiration_time_nanos = txMessage.getExpirationTime().getNanos();
            }
            long auto_renew_period = 0;
            if (txMessage.hasAutoRenewPeriod()) {
                auto_renew_period = txMessage.getAutoRenewPeriod().getSeconds();
            }

            byte[] key = null;
            if (txMessage.hasKey()) {
                key = txMessage.getKey().toByteArray();
            }

            if (doUpdateEntity) {
                long proxy_account_id = entities.createOrGetEntity(txMessage.getProxyAccountID());
                entityId = entities.updateEntity(accountId, expiration_time_sec, expiration_time_nanos,
                        auto_renew_period, key, proxy_account_id);
            } else {
                entityId = entities.createOrGetEntity(accountId);
            }
        } else if (body.hasFileCreate()) {
            if (txRecord.getReceipt().hasFileID()) { // Implies SUCCESS
                FileCreateTransactionBody txMessage = body.getFileCreate();
                long expiration_time_sec = 0;
                long expiration_time_nanos = 0;
                if (txMessage.hasExpirationTime()) {
                    expiration_time_sec = txMessage.getExpirationTime().getSeconds();
                    expiration_time_nanos = txMessage.getExpirationTime().getNanos();
                }
                long auto_renew_period = 0;
                byte[] key = null;
                if (txMessage.hasKeys()) {
                    key = txMessage.getKeys().toByteArray();
                }
                long proxy_account_id = 0;
                entityId = entities.createEntity(txRecord.getReceipt()
                                .getFileID(), expiration_time_sec, expiration_time_nanos, auto_renew_period, key,
                        proxy_account_id);
            }
        } else if (body.hasFileAppend()) {
            if (body.getFileAppend().hasFileID()) {
                entityId = entities.createOrGetEntity(body.getFileAppend().getFileID());
            }
        } else if (body.hasFileDelete()) {
            if (body.getFileDelete().hasFileID()) {
                var fileId = body.getFileDelete().getFileID();
                if (doUpdateEntity) {
                    entityId = entities.deleteEntity(fileId);
                } else {
                    entityId = entities.createOrGetEntity(fileId);
                }
            }
        } else if (body.hasFileUpdate()) {
            FileUpdateTransactionBody txMessage = body.getFileUpdate();
            var fileId = txMessage.getFileID();
            long expiration_time_sec = 0;
            long expiration_time_nanos = 0;
            if (txMessage.hasExpirationTime()) {
                expiration_time_sec = txMessage.getExpirationTime().getSeconds();
                expiration_time_nanos = txMessage.getExpirationTime().getNanos();
            }
            long auto_renew_period = 0;

            byte[] key = null;
            if (txMessage.hasKeys()) {
                key = txMessage.getKeys().toByteArray();
            }

            if (doUpdateEntity) {
                long proxy_account_id = 0;
                entityId = entities
                        .updateEntity(fileId, expiration_time_sec, expiration_time_nanos, auto_renew_period, key,
                                proxy_account_id);
            } else {
                entityId = entities.createOrGetEntity(fileId);
            }
        } else if (body.hasSystemDelete()) {
            if (body.getSystemDelete().hasContractID()) {
                var contractId = body.getSystemDelete().getContractID();
                if (doUpdateEntity) {
                    entityId = entities.deleteEntity(contractId);
                } else {
                    entityId = entities.createOrGetEntity(contractId);
                }
            } else if (body.getSystemDelete().hasFileID()) {
                var fileId = body.getSystemDelete().getFileID();

                if (doUpdateEntity) {
                    entityId = entities.deleteEntity(fileId);
                } else {
                    entities.createOrGetEntity(fileId);
                }
            }
        } else if (body.hasSystemUndelete()) {
            if (body.getSystemUndelete().hasContractID()) {
                var contractId = body.getSystemUndelete().getContractID();
                if (doUpdateEntity) {
                    entityId = entities.unDeleteEntity(contractId);
                } else {
                    entityId = entities.createOrGetEntity(contractId);
                }
            } else if (body.getSystemUndelete().hasFileID()) {
                var fileId = body.getSystemUndelete().getFileID();
                if (doUpdateEntity) {
                    entityId = entities.unDeleteEntity(fileId);
                } else {
                    entityId = entities.createOrGetEntity(fileId);
                }
            }
        }

        if (entityId == 0) {
            // insert null
            sqlInsertTransaction.setObject(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), null);
        } else {
            sqlInsertTransaction.setLong(F_TRANSACTION.CUD_ENTITY_ID.ordinal(), entityId);
        }
        sqlInsertTransaction.setLong(F_TRANSACTION.INITIAL_BALANCE.ordinal(), initialBalance);
        sqlInsertTransaction.addBatch();

        if ((txRecord.hasTransferList()) && parserProperties.isPersistCryptoTransferAmounts()) {
            if (body.hasCryptoCreateAccount() && isSuccessful(txRecord)) {
                insertCryptoCreateTransferList(consensusNs, txRecord, body, txRecord.getReceipt()
                        .getAccountID(), fkPayerAccountId);
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
            if (body.hasCryptoAddClaim()) {
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

    private static void insertTransferList(long consensusTimestamp, TransferList transferList)
            throws SQLException {

        for (int i = 0; i < transferList.getAccountAmountsCount(); ++i) {
            sqlInsertTransferList.setLong(F_TRANSFERLIST.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
            var aa = transferList.getAccountAmounts(i);
            var accountId = aa.getAccountID();
            entities.createOrGetEntity(accountId);
            sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), aa.getAmount());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.REALM_NUM.ordinal(),
                    accountId.getRealmNum());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.ENTITY_NUM.ordinal(),
                    accountId.getAccountNum());
            sqlInsertTransferList.addBatch();
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
            sqlInsertTransferList.setLong(F_TRANSFERLIST.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
            var aa = transferList.getAccountAmounts(i);
            long amount = aa.getAmount();
            var accountId = aa.getAccountID();
            long accountNum = accountId.getAccountNum();
            entities.createOrGetEntity(accountId);

            sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), amount);
            sqlInsertTransferList.setLong(F_TRANSFERLIST.REALM_NUM.ordinal(),
                    accountId.getRealmNum());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.ENTITY_NUM.ordinal(),
                    accountNum);
            sqlInsertTransferList.addBatch();

            if (addInitialBalance && (initialBalance == aa.getAmount()) && (accountNum == createdAccountNum)) {
                addInitialBalance = false;
            }
        }

        if (addInitialBalance) {
            entities.createOrGetEntity(payerAccountId);
            sqlInsertTransferList.setLong(F_TRANSFERLIST.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
            sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), -initialBalance);
            sqlInsertTransferList.setLong(F_TRANSFERLIST.REALM_NUM.ordinal(),
                    payerAccountId.getRealmNum());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.ENTITY_NUM.ordinal(),
                    payerAccountId.getAccountNum());

            sqlInsertTransferList.addBatch();

            entities.createOrGetEntity(createdAccountId);
            sqlInsertTransferList.setLong(F_TRANSFERLIST.CONSENSUS_TIMESTAMP.ordinal(), consensusTimestamp);
            sqlInsertTransferList.setLong(F_TRANSFERLIST.REALM_NUM.ordinal(),
                    createdAccountId.getRealmNum());
            sqlInsertTransferList.setLong(F_TRANSFERLIST.ENTITY_NUM.ordinal(),
                    createdAccountNum);
            sqlInsertTransferList.setLong(F_TRANSFERLIST.AMOUNT.ordinal(), initialBalance);

            sqlInsertTransferList.addBatch();
        }
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
        sqlInsertTransaction.executeBatch();
        sqlInsertTransferList.executeBatch();
        sqlInsertFileData.executeBatch();
        sqlInsertContractCall.executeBatch();
        sqlInsertClaimData.executeBatch();
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

    enum F_TRANSFERLIST {
        ZERO // column indices start at 1, this creates the necessary offset
        , CONSENSUS_TIMESTAMP, AMOUNT, REALM_NUM, ENTITY_NUM
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
