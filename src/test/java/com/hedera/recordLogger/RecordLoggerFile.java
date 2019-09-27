package com.hedera.recordLogger;

import com.google.protobuf.ByteString;

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

import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.FileData;
import com.hedera.mirror.repository.ContractResultRepository;
import com.hedera.mirror.repository.CryptoTransferRepository;
import com.hedera.mirror.repository.EntityRepository;
import com.hedera.mirror.repository.EntityTypeRepository;
import com.hedera.mirror.repository.FileDataRepository;
import com.hedera.mirror.repository.LiveHashRepository;
import com.hedera.mirror.repository.RecordFileRepository;
import com.hedera.mirror.repository.TransactionRepository;
import com.hedera.mirror.repository.TransactionResultRepository;
import com.hedera.recordFileLogger.RecordFileLogger;
import com.hedera.recordFileLogger.RecordFileLogger.INIT_RESULT;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.RealmID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ShardID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

import org.junit.jupiter.api.*;
import org.springframework.test.context.jdbc.Sql;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Sql("classpath:db/scripts/cleanup.sql") // Class manually commits so have to manually cleanup tables
public class RecordLoggerFile extends AbstractRecordFileLoggerTest {

	private static final FileID fileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(1001).build();

    @BeforeEach
    void before() throws Exception {
		assertTrue(RecordFileLogger.start());
		assertEquals(INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
	}

    @AfterEach
    void after() {
    	RecordFileLogger.finish();
    }

    @Test
    void fileCreateTest() throws Exception {

    	final Transaction transaction = fileCreateTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final FileCreateTransactionBody fileCreateTransactionBody = transactionBody.getFileCreate();
    	final TransactionRecord record = transactionRecord(transactionBody);
    	
    	RecordFileLogger.storeRecord(transaction, record);
    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())
                
                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                ,() -> assertEquals(fileCreateTransactionBody.getExpirationTime().getSeconds(), dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertEquals(fileCreateTransactionBody.getExpirationTime().getNanos(), dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertEquals(Utility.timeStampInNanos(fileCreateTransactionBody.getExpirationTime()), dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertArrayEquals(fileCreateTransactionBody.getKeys().toByteArray(), dbFileEntity.get().getKey())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()

                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)
                
                // file data
                ,() -> assertArrayEquals(fileCreateTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }
        
    @Test
    void fileAppendToExistingTest() throws Exception {

		// first create the file
    	final Transaction fileCreateTransaction = fileCreateTransaction();
    	final TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
    	final TransactionRecord recordCreate = transactionRecord(createTransactionBody);

    	RecordFileLogger.storeRecord(fileCreateTransaction, recordCreate);
    	
    	// now append
    	final Transaction transaction = fileAppendTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final FileAppendTransactionBody fileAppendTransactionBody = transactionBody.getFileAppend();
    	final TransactionRecord record = transactionRecord(transactionBody);
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(2, fileDataRepository.count())

                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                
                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // file data
                ,() -> assertArrayEquals(fileAppendTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())

                // Additional entity checks
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())

                //TODO transaction.getSigMap()
         );    	
    }

    @Test
    void fileAppendToNewTest() throws Exception {

    	final Transaction transaction = fileAppendTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final FileAppendTransactionBody fileAppendTransactionBody = transactionBody.getFileAppend();
    	final TransactionRecord record = transactionRecord(transactionBody);
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())
                
                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)

                // record transfer list
                ,() -> assertTransfers(record)

                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()

                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // file data
                ,() -> assertArrayEquals(fileAppendTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())
                
                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())

                //TODO transaction.getSigMap()
         );    	
    }

    @Test
    void fileUpdateAllToExistingTest() throws Exception {

		// first create the file
    	final Transaction fileCreateTransaction = fileCreateTransaction();
    	final TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
    	final TransactionRecord recordCreate = transactionRecord(createTransactionBody);

    	RecordFileLogger.storeRecord(fileCreateTransaction, recordCreate);
    	
    	// now update
    	final Transaction transaction = fileUpdateAllTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final TransactionRecord record = transactionRecord(transactionBody);
    	final FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(2, fileDataRepository.count())
                
                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getSeconds(), dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getNanos(), dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertEquals(Utility.timeStampInNanos(fileUpdateTransactionBody.getExpirationTime()), dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.get().getKey())
                
                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // file data
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }
    
    @Test
    void fileUpdateAllToNewTest() throws Exception {

    	final Transaction transaction = fileUpdateAllTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
    	final TransactionRecord record = transactionRecord(transactionBody);
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())

                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getSeconds(), dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getNanos(), dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertEquals(Utility.timeStampInNanos(fileUpdateTransactionBody.getExpirationTime()), dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.get().getKey())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()

                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // file data
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }    

    @Test
    void fileUpdateContentsToExistingTest() throws Exception {

		// first create the file
    	final Transaction fileCreateTransaction = fileCreateTransaction();
    	final TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
    	final TransactionRecord recordCreate = transactionRecord(createTransactionBody);

    	RecordFileLogger.storeRecord(fileCreateTransaction, recordCreate);
    	
    	// now update
    	final Transaction transaction = fileUpdateContentsTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final TransactionRecord record = transactionRecord(transactionBody);
    	final FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(2, fileDataRepository.count())
                
                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNotNull(dbFileEntity.get().getKey())
                
                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // file data
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }
    
    @Test
    void fileUpdateContentsToNewTest() throws Exception {

    	final Transaction transaction = fileUpdateContentsTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
    	final TransactionRecord record = transactionRecord(transactionBody);
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())

                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNull(dbFileEntity.get().getKey())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()

                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // file data
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }    

    @Test
    void fileUpdateExpiryToExistingTest() throws Exception {

		// first create the file
    	final Transaction fileCreateTransaction = fileCreateTransaction();
    	final TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
    	final TransactionRecord recordCreate = transactionRecord(createTransactionBody);

    	RecordFileLogger.storeRecord(fileCreateTransaction, recordCreate);
    	
    	// now update
    	final Transaction transaction = fileUpdateExpiryTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final TransactionRecord record = transactionRecord(transactionBody);
    	final FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                //TODO: Review below with issue #294, probably should be 1
                ,() -> assertEquals(2, fileDataRepository.count())
                
                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getSeconds(), dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getNanos(), dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertEquals(Utility.timeStampInNanos(fileUpdateTransactionBody.getExpirationTime()), dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNotNull(dbFileEntity.get().getKey())
                
                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }
    
    @Test
    void fileUpdateExpiryToNewTest() throws Exception {

    	final Transaction transaction = fileUpdateExpiryTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
    	final TransactionRecord record = transactionRecord(transactionBody);
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                //TODO: Review below with issue #294, probably should be 0
                ,() -> assertEquals(1, fileDataRepository.count())

                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getSeconds(), dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getNanos(), dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertEquals(Utility.timeStampInNanos(fileUpdateTransactionBody.getExpirationTime()), dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNull(dbFileEntity.get().getKey())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()

                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }    
    
    @Test
    void fileUpdateKeysToExistingTest() throws Exception {

    // first create the file
      final Transaction fileCreateTransaction = fileCreateTransaction();
      final TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
      final TransactionRecord recordCreate = transactionRecord(createTransactionBody);

      RecordFileLogger.storeRecord(fileCreateTransaction, recordCreate);
      
      // now update
      final Transaction transaction = fileUpdateKeysTransaction();
      final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
      final TransactionRecord record = transactionRecord(transactionBody);
      final FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
      
      RecordFileLogger.storeRecord(transaction, record);

      RecordFileLogger.completeFile("", "");
      
      final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
      final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
      final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
      final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
      assertAll(
          // row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                //TODO: Review below with issue #294, probably should be 1
                ,() -> assertEquals(2, fileDataRepository.count())
                
                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.get().getKey())
                
                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }

    @Test
    void fileUpdateKeysToNewTest() throws Exception {

      final Transaction transaction = fileUpdateKeysTransaction();
      final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
      final FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
      final TransactionRecord record = transactionRecord(transactionBody);
      
      RecordFileLogger.storeRecord(transaction, record);

      RecordFileLogger.completeFile("", "");
      
      final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
      final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
      final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
      final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
        
      assertAll(
          // row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                //TODO: Review below with issue #294, probably should be 0
                ,() -> assertEquals(1, fileDataRepository.count())

                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.get().getKey())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()

                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                
                //TODO transaction.getSigMap()
         );    	
    }    
    
    
    @Test
    void fileDeleteToExistingTest() throws Exception {

		// first create the file
    	final Transaction fileCreateTransaction = fileCreateTransaction();
    	final TransactionBody createTransactionBody = TransactionBody.parseFrom(fileCreateTransaction.getBodyBytes());
    	final TransactionRecord recordCreate = transactionRecord(createTransactionBody);

    	RecordFileLogger.storeRecord(fileCreateTransaction, recordCreate);
    	
    	// now update
    	final Transaction transaction = fileDeleteTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final TransactionRecord record = transactionRecord(transactionBody);
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())

                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertTrue(dbFileEntity.get().isDeleted())

                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)

                // Additional entity checks
                ,() -> assertNotNull(dbFileEntity.get().getKey())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                
                //TODO transaction.getSigMap()
         );    	
    }
    @Test
    void fileDeleteToNewTest() throws Exception {

    	final Transaction fileDeleteTransaction = fileDeleteTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(fileDeleteTransaction.getBodyBytes());
    	final TransactionRecord record = transactionRecord(transactionBody);
    	
    	RecordFileLogger.storeRecord(fileDeleteTransaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(0, fileDataRepository.count())

                // record inputs
                ,() -> assertRecord(record, dbTransaction) 

                // receipt
                ,() -> assertFile(record.getReceipt().getFileID(), dbFileEntity)
                
                // record transfer list
                ,() -> assertTransfers(record)
                
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                
                // node
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)
                ,() -> assertTrue(dbFileEntity.get().isDeleted())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()

                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getKey())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                
                //TODO transaction.getSigMap()
         );    	
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody) {
    	final TransactionRecord.Builder record = TransactionRecord.newBuilder();

    	// record
    	final Timestamp consensusTimeStamp = Utility.instantToTimestamp(Instant.now());
    	final long[] transferAccounts = {98, 2002, 3};
    	final long[] transferAmounts = {1000, -2000, 20};
    	final ResponseCodeEnum responseCode = ResponseCodeEnum.SUCCESS;
    	final TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder();

    	// Build the record
    	record.setConsensusTimestamp(consensusTimeStamp);
    	record.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
    	receipt.setFileID(fileId);
    	receipt.setStatus(responseCode);

    	record.setReceipt(receipt.build());
    	record.setTransactionFee(transactionBody.getTransactionFee());
    	//TODO - record.setTransactionHash("");
    	record.setTransactionID(transactionBody.getTransactionID());
    	
    	final TransferList.Builder transferList = TransferList.newBuilder();
    	
    	for (int i=0; i < transferAccounts.length; i++) {
    		AccountAmount.Builder accountAmount = AccountAmount.newBuilder();
    		accountAmount.setAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(transferAccounts[i]));
    		accountAmount.setAmount(transferAmounts[i]);
        	transferList.addAccountAmounts(accountAmount);
    	}
    	
    	record.setTransferList(transferList);
    
    	return record.build();
    }
    
    private Transaction fileCreateTransaction() {
    	
    	final Transaction.Builder transaction = Transaction.newBuilder();
    	final FileCreateTransactionBody.Builder fileCreate = FileCreateTransactionBody.newBuilder();
    	
    	// file create
    	final String fileData = "Hedera hashgraph is great!";
    	final long expiryTimeSeconds = 1571487857L;
    	final int expiryTimeNanos = 181579000;
    	final String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";

    	// Build a transaction
    	fileCreate.setContents(ByteString.copyFromUtf8(fileData));
    	fileCreate.setExpirationTime(Timestamp.newBuilder().setSeconds(expiryTimeSeconds).setNanos(expiryTimeNanos).build());
    	final KeyList.Builder keyList = KeyList.newBuilder();
    	keyList.addKeys(Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build());
    	fileCreate.setKeys(keyList);
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileCreate.setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build());
    	fileCreate.setShardID(ShardID.newBuilder().setShardNum(0));

    	// Transaction body
    	final TransactionBody.Builder body = defaultTransactionBodyBuilder();
    	// body transaction
    	body.setFileCreate(fileCreate.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }
    private Transaction fileAppendTransaction() {
    	
    	final Transaction.Builder transaction = Transaction.newBuilder();
    	final FileAppendTransactionBody.Builder fileAppend = FileAppendTransactionBody.newBuilder();
    	
    	// file append
    	final String fileData = "Hedera hashgraph is even better!";

    	// Build a transaction
    	fileAppend.setContents(ByteString.copyFromUtf8(fileData));
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileAppend.setFileID(fileId);

    	// Transaction body
    	final TransactionBody.Builder body = defaultTransactionBodyBuilder();
    	// body transaction
    	body.setFileAppend(fileAppend.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }
    private Transaction fileUpdateAllTransaction() {
    	
    	final Transaction.Builder transaction = Transaction.newBuilder();
    	final FileUpdateTransactionBody.Builder fileUpdate = FileUpdateTransactionBody.newBuilder();
    	final String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    	
    	// file update
    	final String fileData = "Hedera hashgraph is even better!";

    	// Build a transaction
    	fileUpdate.setContents(ByteString.copyFromUtf8(fileData));
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileUpdate.setFileID(fileId);
    	fileUpdate.setExpirationTime(Utility.instantToTimestamp(Instant.now()));

    	final KeyList.Builder keyList = KeyList.newBuilder();
    	keyList.addKeys(Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build());
    	fileUpdate.setKeys(keyList);
    	
    	// Transaction body
    	final TransactionBody.Builder body = defaultTransactionBodyBuilder();
    	// body transaction
    	body.setFileUpdate(fileUpdate.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }

    private Transaction fileUpdateContentsTransaction() {
    	
    	final Transaction.Builder transaction = Transaction.newBuilder();
    	final FileUpdateTransactionBody.Builder fileUpdate = FileUpdateTransactionBody.newBuilder();
    	
    	// file update
    	final String fileData = "Hedera hashgraph is even better!";

    	// Build a transaction
    	fileUpdate.setContents(ByteString.copyFromUtf8(fileData));
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileUpdate.setFileID(fileId);

    	// Transaction body
    	final TransactionBody.Builder body = defaultTransactionBodyBuilder();
    	// body transaction
    	body.setFileUpdate(fileUpdate.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }

    private Transaction fileUpdateExpiryTransaction() {
    	
    	final Transaction.Builder transaction = Transaction.newBuilder();
    	final FileUpdateTransactionBody.Builder fileUpdate = FileUpdateTransactionBody.newBuilder();
    	
    	// Build a transaction
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileUpdate.setFileID(fileId);
    	fileUpdate.setExpirationTime(Utility.instantToTimestamp(Instant.now()));

    	// Transaction body
    	final TransactionBody.Builder body = defaultTransactionBodyBuilder();
    	// body transaction
    	body.setFileUpdate(fileUpdate.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }

    private Transaction fileUpdateKeysTransaction() {
    	
    	final Transaction.Builder transaction = Transaction.newBuilder();
    	final FileUpdateTransactionBody.Builder fileUpdate = FileUpdateTransactionBody.newBuilder();
    	final String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    	
    	// Build a transaction
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileUpdate.setFileID(fileId);

    	final KeyList.Builder keyList = KeyList.newBuilder();
    	keyList.addKeys(Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build());
    	fileUpdate.setKeys(keyList);
    	
    	// Transaction body
    	final TransactionBody.Builder body = defaultTransactionBodyBuilder();
    	// body transaction
    	body.setFileUpdate(fileUpdate.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }

    private Transaction fileDeleteTransaction() {
    	
     	// transaction id
    	final Transaction.Builder transaction = Transaction.newBuilder();
    	final FileDeleteTransactionBody.Builder fileDelete = FileDeleteTransactionBody.newBuilder();
    	
    	// Build a transaction
     	fileDelete.setFileID(fileId);

    	// Transaction body
     	final TransactionBody.Builder body = defaultTransactionBodyBuilder();
    	// body transaction
    	body.setFileDelete(fileDelete.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }
    
    private Builder defaultTransactionBodyBuilder() {

        final long validDuration = 120;
        final AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2).build();
        final long txFee = 53968962L;
     	final String memo = "File test memo";
        final AccountID nodeAccount = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(3).build();
        
     	final TransactionBody.Builder body = TransactionBody.newBuilder();
    	body.setTransactionFee(txFee);
    	body.setMemo(memo);
    	body.setNodeAccountID(nodeAccount);
    	body.setTransactionID(Utility.getTransactionId(payerAccountId));
    	body.setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration).build());
    	return body;
    }
}
