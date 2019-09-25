package com.hedera.logger;

import com.google.protobuf.ByteString;
//import com.hedera.DBHelper;

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

import com.hedera.IntegrationTest;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.FileData;
import com.hedera.mirror.domain.TransactionResult;
import com.hedera.mirror.repository.ContractResultRepository;
import com.hedera.mirror.repository.CryptoTransferListRepository;
import com.hedera.mirror.repository.EntityRepository;
import com.hedera.mirror.repository.EntityTypeRepository;
import com.hedera.mirror.repository.FileDataRepository;
import com.hedera.mirror.repository.LiveHashRepository;
import com.hedera.mirror.repository.RecordFileRepository;
import com.hedera.mirror.repository.TransactionRepository;
import com.hedera.mirror.repository.TransactionResultRepository;
import com.hedera.recordFileLogger.RecordFileLogger;
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
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

import org.junit.jupiter.api.*;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(OrderAnnotation.class)
@Sql("classpath:db/scripts/cleanup.sql") // Class manually commits so have to manually cleanup tables
public class FileLoggerTestIT extends IntegrationTest {

    // transaction
    private long nodeNum = 3;
    private AccountID nodeAccount = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(nodeNum).build();
    private long txFee = 53968962L;

    // record
	String result = "SUCCESS";
	ResponseCodeEnum responseCode = ResponseCodeEnum.SUCCESS;

    @Resource
    private TransactionRepository transactionRepository;
    @Resource
    private EntityRepository entityRepository;
    @Resource
    private ContractResultRepository contractResultRepository;
    @Resource
    private RecordFileRepository recordFileRepository;
    @Resource
    private CryptoTransferListRepository cryptoTransferListRepository;
    @Resource
    private LiveHashRepository liveHashRepository;
    @Resource
    private FileDataRepository fileDataRepository;
    @Resource
    private TransactionResultRepository transactionResultRepository;
    @Resource
    private EntityTypeRepository entityTypeRepository;

    @BeforeEach
    void before() throws Exception {
		assertTrue(RecordFileLogger.start());
	}

    @AfterEach
    void after() {
    	RecordFileLogger.finish();
    }

    @Test
    @Order(1)
    @DisplayName("File Create test")
    void FileCreateTest() throws Exception {

        // transaction
        // transaction.id
     	long payerAccount = 2;
     	AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(payerAccount).build();
     	long validStartSeconds = 1568895847L;
     	int validStartNanos = 190976000;
     	Timestamp validStart = Timestamp.newBuilder().setSeconds(validStartSeconds).setNanos(validStartNanos).build();
     	TransactionID transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
        
    	// record and transaction
        String memo = "File create memo";

    	// record result
    	long newFile = 1001;
    	FileID newFileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(newFile).build();
    	
    	Transaction transaction = fileCreateTransaction(memo, transactionId);
    	TransactionRecord record = transactionRecord(newFileId, memo, transactionId);
    	TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	FileCreateTransactionBody fileCreateTransactionBody = transactionBody.getFileCreate();
    	
		RecordFileLogger.initFile("FileCreateTest");
    	RecordFileLogger.storeRecord(transaction, record);
    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
    	final Long accountIdFor98 = entityRepository.findEntityByShardRealmNum(0L, 0L, 98L);
    	final Long accountIdFor2002 = entityRepository.findEntityByShardRealmNum(0L, 0L, 2002L);
    	final Long accountIdFor3 = entityRepository.findEntityByShardRealmNum(0L, 0L, 3L);
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferListRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())
                // record inputs
                ,() -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs())
                ,() -> assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee())
                // payer
                ,() -> assertEquals(record.getTransactionID().getAccountID().getShardNum(), dbPayerEntity.get().getEntityShard())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getRealmNum(), dbPayerEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getAccountNum(), dbPayerEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbPayerEntity.get().getEntityTypeId()).get().getName())
                // transaction id
                ,() -> assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs())
                // receipt
                ,() -> assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId())
                ,() -> assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult())
                ,() -> assertEquals(record.getReceipt().getFileID().getShardNum(), dbFileEntity.get().getEntityShard())
                ,() -> assertEquals(record.getReceipt().getFileID().getRealmNum(), dbFileEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getReceipt().getFileID().getFileNum(), dbFileEntity.get().getEntityNum())
                ,() -> assertEquals("file", entityTypeRepository.findById(dbFileEntity.get().getEntityTypeId()).get().getName())
                // record transfer list
                ,() -> assertEquals(1000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor98).orElse(0L))
                ,() -> assertEquals(-2000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor2002).orElse(0L))
                ,() -> assertEquals(20, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor3).orElse(0L))
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                // node
                ,() -> assertEquals(transactionBody.getNodeAccountID().getShardNum(), dbNodeEntity.get().getEntityShard())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getRealmNum(), dbNodeEntity.get().getEntityRealm())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getAccountNum(), dbNodeEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbNodeEntity.get().getEntityTypeId()).get().getName())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                // transaction specifics
                ,() -> assertEquals(fileCreateTransactionBody.getExpirationTime().getSeconds(), dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertEquals(fileCreateTransactionBody.getExpirationTime().getNanos(), dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertEquals(Utility.timeStampInNanos(fileCreateTransactionBody.getExpirationTime()), dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertArrayEquals(fileCreateTransactionBody.getKeys().toByteArray(), dbFileEntity.get().getKey())
                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                // file data
                ,() -> assertArrayEquals(fileCreateTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())
                
                //TODO transaction.getSigMap()
                
         );    	
    }
        
    @Test
    @Order(2)
    @DisplayName("File Append to existing test")
    void FileAppendToExistingTest() throws Exception {

        // transaction
        // transaction.id
     	long payerAccount = 2;
     	AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(payerAccount).build();
     	Timestamp validStart = Utility.instantToTimestamp(Instant.now());
     	TransactionID transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
        
    	// record and transaction
        String memo = "File append memo";

    	// record result
    	long newFile = 1001;
    	FileID appendFileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(newFile).build();

		RecordFileLogger.initFile("FileAppendTest");

		// first create the file
    	Transaction transaction = fileCreateTransaction(memo, transactionId);
    	TransactionRecord recordCreate = transactionRecord(appendFileId, memo, transactionId);

    	RecordFileLogger.storeRecord(transaction, recordCreate);
    	
    	// now append
     	validStart = Utility.instantToTimestamp(Instant.now());
     	transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
    	transaction = fileAppendTransaction(memo, transactionId, appendFileId);
    	TransactionRecord record = transactionRecord(appendFileId, memo, transactionId);
    	TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	FileAppendTransactionBody fileAppendTransactionBody = transactionBody.getFileAppend();
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
    	final Long accountIdFor98 = entityRepository.findEntityByShardRealmNum(0L, 0L, 98L);
    	final Long accountIdFor2002 = entityRepository.findEntityByShardRealmNum(0L, 0L, 2002L);
    	final Long accountIdFor3 = entityRepository.findEntityByShardRealmNum(0L, 0L, 3L);
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferListRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(2, fileDataRepository.count())
                // record inputs
                ,() -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs())
                ,() -> assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee())
                // payer
                ,() -> assertEquals(record.getTransactionID().getAccountID().getShardNum(), dbPayerEntity.get().getEntityShard())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getRealmNum(), dbPayerEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getAccountNum(), dbPayerEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbPayerEntity.get().getEntityTypeId()).get().getName())
                // transaction id
                ,() -> assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs())
                // receipt
                ,() -> assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId())
                ,() -> assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult())
                ,() -> assertEquals(record.getReceipt().getFileID().getShardNum(), dbFileEntity.get().getEntityShard())
                ,() -> assertEquals(record.getReceipt().getFileID().getRealmNum(), dbFileEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getReceipt().getFileID().getFileNum(), dbFileEntity.get().getEntityNum())
                ,() -> assertEquals("file", entityTypeRepository.findById(dbFileEntity.get().getEntityTypeId()).get().getName())
                // record transfer list
                ,() -> assertEquals(1000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor98).orElse(0L))
                ,() -> assertEquals(-2000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor2002).orElse(0L))
                ,() -> assertEquals(20, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor3).orElse(0L))
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                // node
                ,() -> assertEquals(transactionBody.getNodeAccountID().getShardNum(), dbNodeEntity.get().getEntityShard())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getRealmNum(), dbNodeEntity.get().getEntityRealm())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getAccountNum(), dbNodeEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbNodeEntity.get().getEntityTypeId()).get().getName())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                // Additional entity checks
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNotNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                // file data
                ,() -> assertArrayEquals(fileAppendTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())
                
                //TODO transaction.getSigMap()
                
         );    	
    }

    @Test
    @Order(3)
    @DisplayName("File Append to new test")
    void FileAppendToNewTest() throws Exception {

        // transaction
        // transaction.id
     	long payerAccount = 2;
     	AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(payerAccount).build();
     	Timestamp validStart = Utility.instantToTimestamp(Instant.now());
     	TransactionID transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
        
    	// record and transaction
        String memo = "File append memo";

    	// record result
    	long newFile = 1001;
    	FileID appendFileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(newFile).build();

		RecordFileLogger.initFile("FileAppendTest");

     	Transaction transaction = fileAppendTransaction(memo, transactionId, appendFileId);
    	TransactionRecord record = transactionRecord(appendFileId, memo, transactionId);
    	TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	FileAppendTransactionBody fileAppendTransactionBody = transactionBody.getFileAppend();
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
    	final Long accountIdFor98 = entityRepository.findEntityByShardRealmNum(0L, 0L, 98L);
    	final Long accountIdFor2002 = entityRepository.findEntityByShardRealmNum(0L, 0L, 2002L);
    	final Long accountIdFor3 = entityRepository.findEntityByShardRealmNum(0L, 0L, 3L);
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferListRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())
                // record inputs
                ,() -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs())
                ,() -> assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee())
                // payer
                ,() -> assertEquals(record.getTransactionID().getAccountID().getShardNum(), dbPayerEntity.get().getEntityShard())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getRealmNum(), dbPayerEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getAccountNum(), dbPayerEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbPayerEntity.get().getEntityTypeId()).get().getName())
                // transaction id
                ,() -> assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs())
                // receipt
                ,() -> assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId())
                ,() -> assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult())
                ,() -> assertEquals(record.getReceipt().getFileID().getShardNum(), dbFileEntity.get().getEntityShard())
                ,() -> assertEquals(record.getReceipt().getFileID().getRealmNum(), dbFileEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getReceipt().getFileID().getFileNum(), dbFileEntity.get().getEntityNum())
                ,() -> assertEquals("file", entityTypeRepository.findById(dbFileEntity.get().getEntityTypeId()).get().getName())
                // record transfer list
                ,() -> assertEquals(1000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor98).orElse(0L))
                ,() -> assertEquals(-2000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor2002).orElse(0L))
                ,() -> assertEquals(20, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor3).orElse(0L))
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                // node
                ,() -> assertEquals(transactionBody.getNodeAccountID().getShardNum(), dbNodeEntity.get().getEntityShard())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getRealmNum(), dbNodeEntity.get().getEntityRealm())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getAccountNum(), dbNodeEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbNodeEntity.get().getEntityTypeId()).get().getName())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertNull(dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                // file data
                ,() -> assertArrayEquals(fileAppendTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())
                
                //TODO transaction.getSigMap()
                
         );    	
    }

    @Test
    @Order(4)
    @DisplayName("File Update to existing test")
    void FileUpdateToExistingTest() throws Exception {

        // transaction
        // transaction.id
     	long payerAccount = 2;
     	AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(payerAccount).build();
     	Timestamp validStart = Utility.instantToTimestamp(Instant.now());
     	TransactionID transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
        
    	// record and transaction
        String memo = "File update memo";

    	// record result
    	long newFile = 1001;
    	FileID updateFileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(newFile).build();

		RecordFileLogger.initFile("FileUpdateTest");

		// first create the file
    	Transaction transaction = fileCreateTransaction(memo, transactionId);
    	TransactionRecord recordCreate = transactionRecord(updateFileId, memo, transactionId);

    	RecordFileLogger.storeRecord(transaction, recordCreate);
    	
    	// now update
     	validStart = Utility.instantToTimestamp(Instant.now());
     	transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
    	transaction = fileUpdateTransaction(memo, transactionId, updateFileId);
    	TransactionRecord record = transactionRecord(updateFileId, memo, transactionId);
    	TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
    	final Long accountIdFor98 = entityRepository.findEntityByShardRealmNum(0L, 0L, 98L);
    	final Long accountIdFor2002 = entityRepository.findEntityByShardRealmNum(0L, 0L, 2002L);
    	final Long accountIdFor3 = entityRepository.findEntityByShardRealmNum(0L, 0L, 3L);
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferListRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(2, fileDataRepository.count())
                // record inputs
                ,() -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs())
                ,() -> assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee())
                // payer
                ,() -> assertEquals(record.getTransactionID().getAccountID().getShardNum(), dbPayerEntity.get().getEntityShard())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getRealmNum(), dbPayerEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getAccountNum(), dbPayerEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbPayerEntity.get().getEntityTypeId()).get().getName())
                // transaction id
                ,() -> assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs())
                // receipt
                ,() -> assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId())
                ,() -> assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult())
                ,() -> assertEquals(record.getReceipt().getFileID().getShardNum(), dbFileEntity.get().getEntityShard())
                ,() -> assertEquals(record.getReceipt().getFileID().getRealmNum(), dbFileEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getReceipt().getFileID().getFileNum(), dbFileEntity.get().getEntityNum())
                ,() -> assertEquals("file", entityTypeRepository.findById(dbFileEntity.get().getEntityTypeId()).get().getName())
                // record transfer list
                ,() -> assertEquals(1000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor98).orElse(0L))
                ,() -> assertEquals(-2000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor2002).orElse(0L))
                ,() -> assertEquals(20, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor3).orElse(0L))
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                // node
                ,() -> assertEquals(transactionBody.getNodeAccountID().getShardNum(), dbNodeEntity.get().getEntityShard())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getRealmNum(), dbNodeEntity.get().getEntityRealm())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getAccountNum(), dbNodeEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbNodeEntity.get().getEntityTypeId()).get().getName())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getSeconds(), dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getNanos(), dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertEquals(Utility.timeStampInNanos(fileUpdateTransactionBody.getExpirationTime()), dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.get().getKey())
                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                // file data
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())
                
                //TODO transaction.getSigMap()
                
         );    	
    }
    
    @Test
    @Order(5)
    @DisplayName("File Update to new test")
    void FileUpdateToNewTest() throws Exception {

        // transaction
        // transaction.id
     	long payerAccount = 2;
     	AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(payerAccount).build();
     	Timestamp validStart = Utility.instantToTimestamp(Instant.now());
     	TransactionID transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
        
    	// record and transaction
        String memo = "File update memo";

    	// record result
    	long newFile = 1001;
    	FileID updateFileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(newFile).build();

		RecordFileLogger.initFile("FileUpdateTest");

    	validStart = Utility.instantToTimestamp(Instant.now());
     	transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
     	Transaction transaction = fileUpdateTransaction(memo, transactionId, updateFileId);
    	TransactionRecord record = transactionRecord(updateFileId, memo, transactionId);
    	TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	FileUpdateTransactionBody fileUpdateTransactionBody = transactionBody.getFileUpdate();
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<FileData> dbfileData = fileDataRepository.findById(dbTransaction.get().getConsensusNs());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
    	final Long accountIdFor98 = entityRepository.findEntityByShardRealmNum(0L, 0L, 98L);
    	final Long accountIdFor2002 = entityRepository.findEntityByShardRealmNum(0L, 0L, 2002L);
    	final Long accountIdFor3 = entityRepository.findEntityByShardRealmNum(0L, 0L, 3L);
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferListRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())
                // record inputs
                ,() -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs())
                ,() -> assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee())
                // payer
                ,() -> assertEquals(record.getTransactionID().getAccountID().getShardNum(), dbPayerEntity.get().getEntityShard())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getRealmNum(), dbPayerEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getAccountNum(), dbPayerEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbPayerEntity.get().getEntityTypeId()).get().getName())
                // transaction id
                ,() -> assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs())
                // receipt
                ,() -> assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId())
                ,() -> assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult())
                ,() -> assertEquals(record.getReceipt().getFileID().getShardNum(), dbFileEntity.get().getEntityShard())
                ,() -> assertEquals(record.getReceipt().getFileID().getRealmNum(), dbFileEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getReceipt().getFileID().getFileNum(), dbFileEntity.get().getEntityNum())
                ,() -> assertEquals("file", entityTypeRepository.findById(dbFileEntity.get().getEntityTypeId()).get().getName())
                // record transfer list
                ,() -> assertEquals(1000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor98).orElse(0L))
                ,() -> assertEquals(-2000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor2002).orElse(0L))
                ,() -> assertEquals(20, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor3).orElse(0L))
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                // node
                ,() -> assertEquals(transactionBody.getNodeAccountID().getShardNum(), dbNodeEntity.get().getEntityShard())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getRealmNum(), dbNodeEntity.get().getEntityRealm())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getAccountNum(), dbNodeEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbNodeEntity.get().getEntityTypeId()).get().getName())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getSeconds(), dbFileEntity.get().getExpiryTimeSeconds())
                ,() -> assertEquals(fileUpdateTransactionBody.getExpirationTime().getNanos(), dbFileEntity.get().getExpiryTimeNanos())
                ,() -> assertEquals(Utility.timeStampInNanos(fileUpdateTransactionBody.getExpirationTime()), dbFileEntity.get().getExpiryTimeNs())
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getKeys().toByteArray(), dbFileEntity.get().getKey())
                // Additional entity checks
                ,() -> assertNull(dbFileEntity.get().getAutoRenewPeriod())
                ,() -> assertNull(dbFileEntity.get().getProxyAccountId())
                ,() -> assertFalse(dbFileEntity.get().isDeleted())
                // file data
                ,() -> assertArrayEquals(fileUpdateTransactionBody.getContents().toByteArray(), dbfileData.get().getFileData())
                
                //TODO transaction.getSigMap()
                
         );    	
    }    

    @Test
    @Order(6)
    @DisplayName("File Delete to existing test")
    void FileDeleteToExistingTest() throws Exception {

        // transaction
        // transaction.id
     	long payerAccount = 2;
     	AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(payerAccount).build();
     	Timestamp validStart = Utility.instantToTimestamp(Instant.now());
     	TransactionID transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
        
    	// record and transaction
        String memo = "File delete memo";

    	// record result
    	long deleteFile = 1001;
    	FileID deleteFileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(deleteFile).build();

		RecordFileLogger.initFile("FileDeleteTest");

		// first create the file
    	Transaction transaction = fileCreateTransaction(memo, transactionId);
    	TransactionRecord recordCreate = transactionRecord(deleteFileId, memo, transactionId);

    	RecordFileLogger.storeRecord(transaction, recordCreate);
    	
    	// now update
     	validStart = Utility.instantToTimestamp(Instant.now());
     	transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
    	transaction = fileDeleteTransaction(memo, transactionId, deleteFileId);
    	TransactionRecord record = transactionRecord(deleteFileId, memo, transactionId);
    	TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
    	final Long accountIdFor98 = entityRepository.findEntityByShardRealmNum(0L, 0L, 98L);
    	final Long accountIdFor2002 = entityRepository.findEntityByShardRealmNum(0L, 0L, 2002L);
    	final Long accountIdFor3 = entityRepository.findEntityByShardRealmNum(0L, 0L, 3L);
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(2, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(6, cryptoTransferListRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(1, fileDataRepository.count())
                // record inputs
                ,() -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs())
                ,() -> assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee())
                // payer
                ,() -> assertEquals(record.getTransactionID().getAccountID().getShardNum(), dbPayerEntity.get().getEntityShard())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getRealmNum(), dbPayerEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getAccountNum(), dbPayerEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbPayerEntity.get().getEntityTypeId()).get().getName())
                // transaction id
                ,() -> assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs())
                // receipt
                ,() -> assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId())
                ,() -> assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult())
                ,() -> assertEquals(record.getReceipt().getFileID().getShardNum(), dbFileEntity.get().getEntityShard())
                ,() -> assertEquals(record.getReceipt().getFileID().getRealmNum(), dbFileEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getReceipt().getFileID().getFileNum(), dbFileEntity.get().getEntityNum())
                ,() -> assertEquals("file", entityTypeRepository.findById(dbFileEntity.get().getEntityTypeId()).get().getName())
                // record transfer list
                ,() -> assertEquals(1000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor98).orElse(0L))
                ,() -> assertEquals(-2000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor2002).orElse(0L))
                ,() -> assertEquals(20, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor3).orElse(0L))
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                // node
                ,() -> assertEquals(transactionBody.getNodeAccountID().getShardNum(), dbNodeEntity.get().getEntityShard())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getRealmNum(), dbNodeEntity.get().getEntityRealm())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getAccountNum(), dbNodeEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbNodeEntity.get().getEntityTypeId()).get().getName())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertTrue(dbFileEntity.get().isDeleted())
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
    @Order(7)
    @DisplayName("File Delete to new test")
    void FileDeleteToNewTest() throws Exception {

        // transaction
        // transaction.id
     	long payerAccount = 2;
     	AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(payerAccount).build();
     	Timestamp validStart = Utility.instantToTimestamp(Instant.now());
     	TransactionID transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
        
    	// record and transaction
        String memo = "File delete memo";

    	// record result
    	long deleteFile = 1001;
    	FileID deleteFileId = FileID.newBuilder().setShardNum(0).setRealmNum(0).setFileNum(deleteFile).build();

		RecordFileLogger.initFile("FileDeleteTest");

     	validStart = Utility.instantToTimestamp(Instant.now());
     	transactionId = TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart).build();
    	Transaction transaction = fileDeleteTransaction(memo, transactionId, deleteFileId);
    	TransactionRecord record = transactionRecord(deleteFileId, memo, transactionId);
    	TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	
    	RecordFileLogger.storeRecord(transaction, record);

    	RecordFileLogger.completeFile("", "");
    	
    	final Optional<com.hedera.mirror.domain.Transaction> dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp()));
    	final Optional<Entities> dbFileEntity = entityRepository.findById(dbTransaction.get().getEntityId());
    	final Optional<Entities> dbNodeEntity = entityRepository.findById(dbTransaction.get().getNodeAccountId());
    	final Optional<Entities> dbPayerEntity = entityRepository.findById(dbTransaction.get().getPayerAccountId());
    	final Optional<TransactionResult> dbResult = transactionResultRepository.findById(dbTransaction.get().getResultId());
    	final Long accountIdFor98 = entityRepository.findEntityByShardRealmNum(0L, 0L, 98L);
    	final Long accountIdFor2002 = entityRepository.findEntityByShardRealmNum(0L, 0L, 2002L);
    	final Long accountIdFor3 = entityRepository.findEntityByShardRealmNum(0L, 0L, 3L);
        
    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(5, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferListRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(0, fileDataRepository.count())
                // record inputs
                ,() -> assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.get().getConsensusNs())
                ,() -> assertEquals(record.getTransactionFee(), dbTransaction.get().getChargedTxFee())
                // payer
                ,() -> assertEquals(record.getTransactionID().getAccountID().getShardNum(), dbPayerEntity.get().getEntityShard())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getRealmNum(), dbPayerEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getTransactionID().getAccountID().getAccountNum(), dbPayerEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbPayerEntity.get().getEntityTypeId()).get().getName())
                // transaction id
                ,() -> assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.get().getValidStartNs())
                // receipt
                ,() -> assertEquals(record.getReceipt().getStatusValue(), dbResult.get().getProtobufId())
                ,() -> assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.get().getResult())
                ,() -> assertEquals(record.getReceipt().getFileID().getShardNum(), dbFileEntity.get().getEntityShard())
                ,() -> assertEquals(record.getReceipt().getFileID().getRealmNum(), dbFileEntity.get().getEntityRealm())
                ,() -> assertEquals(record.getReceipt().getFileID().getFileNum(), dbFileEntity.get().getEntityNum())
                ,() -> assertEquals("file", entityTypeRepository.findById(dbFileEntity.get().getEntityTypeId()).get().getName())
                // record transfer list
                ,() -> assertEquals(1000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor98).orElse(0L))
                ,() -> assertEquals(-2000, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor2002).orElse(0L))
                ,() -> assertEquals(20, cryptoTransferListRepository.amountByTransactionAndAccount(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountIdFor3).orElse(0L))
                // transaction body inputs
                ,() -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.get().getMemo())
                // node
                ,() -> assertEquals(transactionBody.getNodeAccountID().getShardNum(), dbNodeEntity.get().getEntityShard())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getRealmNum(), dbNodeEntity.get().getEntityRealm())
                ,() -> assertEquals(transactionBody.getNodeAccountID().getAccountNum(), dbNodeEntity.get().getEntityNum())
                ,() -> assertEquals("account", entityTypeRepository.findById(dbNodeEntity.get().getEntityTypeId()).get().getName())
                //TODO transactionBody.getTransactionFee()
                //TODO transactionBody.getTransactionValidDuration()
                ,() -> assertTrue(dbFileEntity.get().isDeleted())
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

    private TransactionRecord transactionRecord(FileID fileId, String memo, TransactionID transactionId) {
    	TransactionRecord.Builder record = TransactionRecord.newBuilder();

    	// record
    	Timestamp consensusTimeStamp = Utility.instantToTimestamp(Instant.now());
    	long[] transferAccounts = {98, 2002, 3};
    	long[] transferAmounts = {1000, -2000, 20};

    	// Build the record
    	record.setConsensusTimestamp(consensusTimeStamp);
    	record.setMemoBytes(ByteString.copyFromUtf8(memo));
    	TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder();
    	receipt.setFileID(fileId);
    	receipt.setStatus(responseCode);

    	record.setReceipt(receipt.build());
    	record.setTransactionFee(txFee);
    	//TODO - record.setTransactionHash("");
    	record.setTransactionID(transactionId);
    	
    	TransferList.Builder transferList = TransferList.newBuilder();
    	
    	for (int i=0; i < transferAccounts.length; i++) {
    		AccountAmount.Builder accountAmount = AccountAmount.newBuilder();
    		accountAmount.setAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(transferAccounts[i]));
    		accountAmount.setAmount(transferAmounts[i]);
        	transferList.addAccountAmounts(accountAmount);
    	}
    	
    	record.setTransferList(transferList);
    
    	return record.build();
    }
    
    private Transaction fileCreateTransaction(String memo, TransactionID transactionId) {
    	
    	Transaction.Builder transaction = Transaction.newBuilder();
    	TransactionBody.Builder body = TransactionBody.newBuilder();
    	FileCreateTransactionBody.Builder fileCreate = FileCreateTransactionBody.newBuilder();
    	
    	// file create
     	long validDuration = 120;
    	String fileData = "Hedera hashgraph is great!";
    	long expiryTimeSeconds = 1571487857L;
    	int expiryTimeNanos = 181579000;
    	String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";

    	// Build a transaction
    	fileCreate.setContents(ByteString.copyFromUtf8(fileData));
    	fileCreate.setExpirationTime(Timestamp.newBuilder().setSeconds(expiryTimeSeconds).setNanos(expiryTimeNanos).build());
    	KeyList.Builder keyList = KeyList.newBuilder();
    	keyList.addKeys(Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build());
    	fileCreate.setKeys(keyList);
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileCreate.setRealmID(RealmID.newBuilder().setShardNum(0).setRealmNum(0).build());
    	fileCreate.setShardID(ShardID.newBuilder().setShardNum(0));

    	// Transaction body
    	body.setTransactionFee(txFee);
    	body.setMemo(memo);
    	body.setNodeAccountID(nodeAccount);
    	body.setTransactionID(transactionId);
    	body.setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration).build());
    	// body transaction
    	body.setFileCreate(fileCreate.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }
    private Transaction fileAppendTransaction(String memo, TransactionID transactionId, FileID fileId) {
    	
    	Transaction.Builder transaction = Transaction.newBuilder();
    	TransactionBody.Builder body = TransactionBody.newBuilder();
    	FileAppendTransactionBody.Builder fileAppend = FileAppendTransactionBody.newBuilder();
    	
    	// file append
     	long validDuration = 120;
    	String fileData = "Hedera hashgraph is even better!";

    	// Build a transaction
    	fileAppend.setContents(ByteString.copyFromUtf8(fileData));
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileAppend.setFileID(fileId);

    	// Transaction body
    	body.setTransactionFee(txFee);
    	body.setMemo(memo);
    	body.setNodeAccountID(nodeAccount);
    	body.setTransactionID(transactionId);
    	body.setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration).build());
    	// body transaction
    	body.setFileAppend(fileAppend.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }
    private Transaction fileUpdateTransaction(String memo, TransactionID transactionId, FileID fileId) {
    	
    	Transaction.Builder transaction = Transaction.newBuilder();
    	TransactionBody.Builder body = TransactionBody.newBuilder();
    	FileUpdateTransactionBody.Builder fileUpdate = FileUpdateTransactionBody.newBuilder();
    	String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    	
    	// file update
     	long validDuration = 120;
    	String fileData = "Hedera hashgraph is even better!";

    	// Build a transaction
    	fileUpdate.setContents(ByteString.copyFromUtf8(fileData));
    	//TODO - transactionBody.setNewRealmAdminKey(value);
    	fileUpdate.setFileID(fileId);
    	fileUpdate.setExpirationTime(Utility.instantToTimestamp(Instant.now()));

    	KeyList.Builder keyList = KeyList.newBuilder();
    	keyList.addKeys(Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build());
    	fileUpdate.setKeys(keyList);
    	
    	// Transaction body
    	body.setTransactionFee(txFee);
    	body.setMemo(memo);
    	body.setNodeAccountID(nodeAccount);
    	body.setTransactionID(transactionId);
    	body.setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration).build());
    	// body transaction
    	body.setFileUpdate(fileUpdate.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }
    private Transaction fileDeleteTransaction(String memo, TransactionID transactionId, FileID fileId) {
    	
    	Transaction.Builder transaction = Transaction.newBuilder();
    	TransactionBody.Builder body = TransactionBody.newBuilder();
    	FileDeleteTransactionBody.Builder fileDelete = FileDeleteTransactionBody.newBuilder();
    	
    	// file delete
     	long validDuration = 120;

    	// Build a transaction
     	fileDelete.setFileID(fileId);

    	// Transaction body
    	body.setTransactionFee(txFee);
    	body.setMemo(memo);
    	body.setNodeAccountID(nodeAccount);
    	body.setTransactionID(transactionId);
    	body.setTransactionValidDuration(Duration.newBuilder().setSeconds(validDuration).build());
    	// body transaction
    	body.setFileDelete(fileDelete.build());

    	transaction.setBodyBytes(body.build().toByteString());
    	//TODO-body.setSigMap(value);

    	return transaction.build();
    }
}
