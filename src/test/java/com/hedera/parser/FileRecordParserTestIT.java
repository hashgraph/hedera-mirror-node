package com.hedera.parser;

import com.hedera.DBCryptoTransfers;
import com.hedera.DBEntity;
import com.hedera.DBFile;
import com.hedera.DBHelper;
import com.hedera.DBTransaction;

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

import com.hedera.FileCopier;
import com.hedera.IntegrationTest;
import com.hedera.configLoader.ConfigLoader;
import com.hedera.mirror.config.RecordProperties;
import com.hedera.mirror.domain.ApplicationStatusCode;
import com.hedera.mirror.repository.ApplicationStatusRepository;
import com.hedera.mirror.repository.TransactionsRepository;
import com.hedera.utilities.Utility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.nio.file.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(OrderAnnotation.class)
public class FileRecordParserTestIT {

    @TempDir
    Path dataPath;

    @TempDir
    Path s3Path;

    private Path validPath;
    private FileCopier fileCopier;
    private RecordFileParser recordFileParser;
    private ApplicationStatusRepository applicationStatusRepository;
    private TransactionsRepository transactionsRepository;
    private RecordProperties recordProperties = new RecordProperties();
    private String type = "file";
    // test setup
	private static File[] files;
    @BeforeEach
    void before() throws Exception {
    }

    @AfterEach
    void after() {
    }

    @Test
    @Order(1)
    @DisplayName("Parse record files")
    void parseRecordFiles() throws Exception {
        ConfigLoader.setDownloadToDir(dataPath.toAbsolutePath().toString());
        
        DBHelper.deleteDatabaseData();
        
        recordFileParser = new RecordFileParser(applicationStatusRepository, recordProperties);
        
        validPath = Paths.get(ConfigLoader.getDefaultParseDir(ConfigLoader.OPERATION_TYPE.RECORDS));
        
    	fileCopier = FileCopier.create(Utility.getResource("data").toPath(), s3Path)
                .from("recordstreams", "fileTransactions")
                .to(validPath);
    	
        fileCopier.copy();
        files = validPath.toFile().listFiles();
        Arrays.sort(files);
        recordFileParser.parse();
		
    }
    @Test
    @Order(2)
    @DisplayName("Parse record files - check counts")
    void parseRecordFilesCheckCounts() throws Exception {
    	System.out.println( transactionsRepository.count());
    	assertAll(
                () -> assertEquals(5, DBHelper.countRecordFiles())
                ,() -> assertEquals(14, transactionsRepository.count()) //DBHelper.countTransactions())
                ,() -> assertEquals(7, DBHelper.countEntities())
                ,() -> assertEquals(0, DBHelper.countContractResult())
                ,() -> assertEquals(54, DBHelper.countCryptoTransferLists())
                ,() -> assertEquals(0, DBHelper.countLiveHashes())
                ,() -> assertEquals(7, DBHelper.countFileData())
        );    	
    }
        
    @Test
    @Order(2)
    @DisplayName("Parse record files - check application status")
    void parseRecordFilesCheckApplicationStatus() throws Exception {
    	assertEquals("19643da6700d8767eb53fc81880c6a02366f2f33fc6541b366310d1de5b104e6bb9c0e2ac23c082b502b0ca503e96564", 
    			applicationStatusRepository.findById(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH));
    }

    @Test
    @Order(2)
    @DisplayName("Parse record files - check simple file create")
    void parseRecordFilesCheckFileCreateSimple() throws Exception {
    	// connect to database, read data, assert values
    	long nodeAccount = 3;
    	String memo = "File create memo";
    	String transactionType = "FILECREATE";
    	String result = "SUCCESS";
    	String fileData = "Hedera hashgraph is great!";
    	long payerAccount = 2;
    	long txFee = 53968962;
    	long initialBalance = 0;
    	long newFile = 1001;
    	String recordFile = files[0].getName();
    	long validStartNS = 1568895847190976000L;
    	long consensusNs = 1568895858019452000L;
    	long expiryTimeSeconds = 1571487857L;
    	long expiryTimeNanos = 181579000L;
    	long expiryTimeNs = 1571487857181579000L;
    	String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    			
    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newFile, recordFile, consensusNs);

    	DBEntity entity = new DBEntity();
    	entity.getEntityDetails(transaction.cudEntity);
    	
    	String dbFileData = DBFile.getFileData(consensusNs);

    	assertAll(
    	    	() -> assertEquals(3, DBCryptoTransfers.checkCount(transaction.consensusNs))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -53968962))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 2111740))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 51857222))
    	    	,() -> assertEquals(0, entity.autoRenewPeriod)
    	    	,() -> assertEquals(0, entity.proxyAccountNum)
    	    	,() -> assertEquals(key, entity.key)
    	    	,() -> assertNull(entity.ed25519KeyHex)
    	    	,() -> assertEquals(newFile, entity.entityNum)
    	    	,() -> assertEquals(0, entity.entityShard)
    	    	,() -> assertEquals(0, entity.entityRealm)
    	    	,() -> assertEquals(type, entity.entityType)
    	    	,() -> assertFalse(entity.deleted)
    	    	,() -> assertEquals(fileData, dbFileData)
    	    	,() -> assertEquals(expiryTimeSeconds, entity.expiryTimeSeconds)
    	    	,() -> assertEquals(expiryTimeNanos, entity.expiryTimeNanos)
    	    	,() -> assertEquals(expiryTimeNs, entity.expiryTimeNs)
        );    	
    }

    @Test
    @Order(2)
    @DisplayName("Parse record files - check file append 1")
    void parseRecordFilesCheckFirstFileAppend() throws Exception {
    	// connect to database, read data, assert values
    	long nodeAccount = 3;
    	long payerAccount = 2;
    	String memo = "File append memo 1";
    	String transactionType = "FILEAPPEND";
    	String result = "SUCCESS";
    	String fileData = "... but it gets better !";
    	long txFee = 33028498;
    	long newFile = 1002;
    	long validStartNS = 1568895850066517000L;
    	long consensusNs = 1568895860135324000L;
    	long initialBalance = 0;
    	String recordFile = files[1].getName();
    	long expiryTimeSeconds = 1571487859L;
    	long expiryTimeNanos = 51103000L;
    	long expiryTimeNs = 1571487859051103000L;
    	String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";

    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newFile, recordFile, consensusNs);
    	DBEntity entity = new DBEntity();
    	entity.getEntityDetails(transaction.cudEntity);

    	String dbFileData = DBFile.getFileData(consensusNs);

    	assertAll(
    	    	() -> assertEquals(3, DBCryptoTransfers.checkCount(transaction.consensusNs))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -33028498))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 2012025))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 31016473))
    	    	,() -> assertEquals(0, entity.autoRenewPeriod)
    	    	,() -> assertEquals(0, entity.proxyAccountNum)
    	    	,() -> assertEquals(key, entity.key)
    	    	,() -> assertEquals(newFile, entity.entityNum)
    	    	,() -> assertEquals(0, entity.entityShard)
    	    	,() -> assertEquals(0, entity.entityRealm)
    	    	,() -> assertEquals(type, entity.entityType)
    	    	,() -> assertFalse(entity.deleted)
    	    	,() -> assertEquals(fileData, dbFileData)
    	    	,() -> assertEquals(expiryTimeNanos, entity.expiryTimeNanos)
    	    	,() -> assertEquals(expiryTimeSeconds, entity.expiryTimeSeconds)
    	    	,() -> assertEquals(expiryTimeNs, entity.expiryTimeNs)
        );    	
    }

    @Test
    @Order(2)
    @DisplayName("Parse record files - check file append 2")
    void parseRecordFilesCheckSecondFileAppend() throws Exception {
    	// connect to database, read data, assert values
    	long nodeAccount = 3;
    	long payerAccount = 2;
    	String memo = "File append memo 2";
    	String transactionType = "FILEAPPEND";
    	String result = "SUCCESS";
    	String fileData = "... and better !";
    	long txFee = 32958971;
    	long newFile = 1002;
    	long validStartNS = 1568895851104674000L;
    	long consensusNs = 1568895861175848000L;
    	long initialBalance = 0;
    	String recordFile = files[1].getName();
    	long expiryTimeSeconds = 1571487859L;
    	long expiryTimeNanos = 51103000L;
    	long expiryTimeNs = 1571487859051103000L;
    	String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    			
    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newFile, recordFile, consensusNs);
    	DBEntity entity = new DBEntity();
    	entity.getEntityDetails(transaction.cudEntity);

    	String dbFileData = DBFile.getFileData(consensusNs);

    	assertAll(
    	    	() -> assertEquals(3, DBCryptoTransfers.checkCount(transaction.consensusNs))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -32958971))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 2007219))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 30951752))
    	    	,() -> assertEquals(0, entity.autoRenewPeriod)
    	    	,() -> assertEquals(0, entity.proxyAccountNum)
    	    	,() -> assertEquals(key, entity.key)
    	    	,() -> assertEquals(newFile, entity.entityNum)
    	    	,() -> assertEquals(0, entity.entityShard)
    	    	,() -> assertEquals(0, entity.entityRealm)
    	    	,() -> assertEquals(type, entity.entityType)
    	    	,() -> assertFalse(entity.deleted)
    	    	,() -> assertEquals(fileData, dbFileData)
    	    	,() -> assertEquals(expiryTimeNanos, entity.expiryTimeNanos)
    	    	,() -> assertEquals(expiryTimeSeconds, entity.expiryTimeSeconds)
    	    	,() -> assertEquals(expiryTimeNs, entity.expiryTimeNs)
        );    	
    }
    @Test
    @Order(2)
    @DisplayName("Parse record files - check file update")
    void parseRecordFilesCheckFileUpdate() throws Exception {
    	// connect to database, read data, assert values
    	long nodeAccount = 3;
    	long payerAccount = 2;
    	String memo = "File update memo";
    	String transactionType = "FILEUPDATE";
    	String result = "SUCCESS";
    	String fileData = "So good";
    	long txFee = 539219;
    	long newFile = 1003;
    	long validStartNS = 1568895853150434000L;
    	long consensusNs = 1568895863199520000L;
    	long initialBalance = 0;
    	String recordFile = files[1].getName();
    	long expiryTimeSeconds = 1569414263L;
    	long expiryTimeNanos = 148731000L;
    	long expiryTimeNs = 1569414263148731000L;
    	String key = "0a221220aba6af46b144798057f9e9db7e07f1fd6b02a593afa8bf2bb6bf59e3c85b3377";

    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newFile, recordFile, consensusNs);
    	DBEntity entity = new DBEntity();
    	entity.getEntityDetails(transaction.cudEntity);

    	String dbFileData = DBFile.getFileData(consensusNs);
    	assertAll(
    	    	() -> assertEquals(3, DBCryptoTransfers.checkCount(transaction.consensusNs))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -539219))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 21054))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 518165))
    	    	,() -> assertEquals(0, entity.autoRenewPeriod)
    	    	,() -> assertEquals(0, entity.proxyAccountNum)
    	    	,() -> assertEquals(key, entity.key)
    	    	,() -> assertEquals(newFile, entity.entityNum)
    	    	,() -> assertEquals(0, entity.entityShard)
    	    	,() -> assertEquals(0, entity.entityRealm)
    	    	,() -> assertEquals(type, entity.entityType)
    	    	,() -> assertFalse(entity.deleted)
    	    	,() -> assertEquals(fileData, dbFileData)
    	    	,() -> assertEquals(expiryTimeNanos, entity.expiryTimeNanos)
    	    	,() -> assertEquals(expiryTimeSeconds, entity.expiryTimeSeconds)
    	    	,() -> assertEquals(expiryTimeNs, entity.expiryTimeNs)
        );    	
    }
    @Test
    @Order(2)
    @DisplayName("Parse record files - check file delete")
    void parseRecordFilesCheckFileDelete() throws Exception {
    	// connect to database, read data, assert values
    	long nodeAccount = 3;
    	long payerAccount = 2;
    	String memo = "File delete memo";
    	String transactionType = "FILEDELETE";
    	String result = "SUCCESS";
    	String fileData = "";
    	long txFee = 5908607;
    	long newFile = 1004;
    	long validStartNS = 1568895855194216000L;
    	long consensusNs = 1568895865213269001L;
    	long initialBalance = 0;
    	String recordFile = files[2].getName();
    	long expiryTimeSeconds = 1571487864L;
    	long expiryTimeNanos = 175326000L;
    	long expiryTimeNs = 1571487864175326000L;
    	String key = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";

    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newFile, recordFile, consensusNs);
    	DBEntity entity = new DBEntity();
    	entity.getEntityDetails(transaction.cudEntity);

    	String dbFileData = DBFile.getFileData(consensusNs);
    	assertAll(
    	    	() -> assertEquals(3, DBCryptoTransfers.checkCount(transaction.consensusNs))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -5908607))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 359981))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 5548626))
    	    	,() -> assertEquals(0, entity.autoRenewPeriod)
    	    	,() -> assertEquals(0, entity.proxyAccountNum)
    	    	,() -> assertEquals(key, entity.key)
    	    	,() -> assertEquals(newFile, entity.entityNum)
    	    	,() -> assertEquals(0, entity.entityShard)
    	    	,() -> assertEquals(0, entity.entityRealm)
    	    	,() -> assertEquals(type, entity.entityType)
    	    	,() -> assertTrue(entity.deleted)
    	    	,() -> assertEquals(fileData, dbFileData)
    	    	,() -> assertEquals(expiryTimeNanos, entity.expiryTimeNanos)
    	    	,() -> assertEquals(expiryTimeSeconds, entity.expiryTimeSeconds)
    	    	,() -> assertEquals(expiryTimeNs, entity.expiryTimeNs)
        );    	
    }
}
