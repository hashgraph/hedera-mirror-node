package com.hedera.parser;

import com.hedera.DBCryptoTransfers;
import com.hedera.DBEntity;
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
import com.hedera.utilities.Utility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.*;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(OrderAnnotation.class)
@ExtendWith(MockitoExtension.class)
public class CryptoRecordParserTestIT extends IntegrationTest {

    @TempDir
    Path dataPath;

    @TempDir
    Path s3Path;

    private Path validPath;
    private FileCopier fileCopier;
    private RecordFileParser recordFileParser;
    private RecordProperties recordProperties = new RecordProperties();
    private String type = "account";
    private ApplicationStatusRepository applicationStatusRepository;
    
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
                .from("recordstreams", "cryptoTransactions")
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
    	assertAll(
                () -> assertEquals(5, DBHelper.countRecordFiles())
                ,() -> assertEquals(14, DBHelper.countTransactions())
                ,() -> assertEquals(9, DBHelper.countEntities())
                ,() -> assertEquals(0, DBHelper.countContractResult())
                ,() -> assertEquals(68, DBHelper.countCryptoTransferLists())
                ,() -> assertEquals(0, DBHelper.countLiveHashes())
                ,() -> assertEquals(0, DBHelper.countFileData())
        );    	
    }
        
    @Test
    @Order(2)
    @DisplayName("Parse record files - check application status")
    void parseRecordFilesCheckApplicationStatus() throws Exception {
    	assertEquals("fd6f669c574a6661fe70c420a1a170ef583559fd949598706950b14403d1b64aec4d1becb149673e17817971f17465db"
    			, applicationStatusRepository.findByStatusCode(ApplicationStatusCode.LAST_PROCESSED_RECORD_HASH));
    }

    @Test
    @Order(2)
    @DisplayName("Parse record files - check simple crypto create")
    void parseRecordFilesCheckCryptoCreateSimple() throws Exception {
    	// connect to database, read data, assert values
    	long nodeAccount = 3;
    	String memo = "";
    	String transactionType = "CRYPTOCREATEACCOUNT";
    	String result = "SUCCESS";
    	long payerAccount = 2;
    	long txFee = 8404260;
    	long initialBalance = 1000;
    	long newAccount = 1001;
    	String recordFile = files[0].getName();
    	long validStartNS = 1568033813854179000L;
    	long consensusNs = 1568033824458639000L;
    	String key = "1220019971fc0db78dec75b8c46d795294f0520fdd9177fb410db9f9376c1c3da23a";
    	String ed25519Key = "019971fc0db78dec75b8c46d795294f0520fdd9177fb410db9f9376c1c3da23a";

    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newAccount, recordFile, consensusNs);

    	DBEntity createdAccount = new DBEntity();
    	createdAccount.getEntityDetails(transaction.cudEntity);
    	
    	assertAll(
    	    	() -> assertEquals(5, DBCryptoTransfers.checkCount(transaction.consensusNs))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -8404260))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 507679))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 7896581))
    	    	,() -> assertEquals(2592000, createdAccount.autoRenewPeriod)
    	    	,() -> assertEquals(0, createdAccount.proxyAccountNum)
    	    	,() -> assertEquals(key, createdAccount.key)
    	    	,() -> assertEquals(ed25519Key, createdAccount.ed25519KeyHex)
    	    	,() -> assertEquals(newAccount, createdAccount.entityNum)
    	    	,() -> assertEquals(0, createdAccount.entityShard)
    	    	,() -> assertEquals(0, createdAccount.entityRealm)
    	    	,() -> assertEquals(type, createdAccount.entityType)
    	    	,() -> assertFalse(createdAccount.deleted)
        );    	

    	//TODO: assertEquals(10000, createdAccount.receiveRecordThreshold);
    	//TODO: assertEquals(15000, createdAccount.sendRecordThreshold);
    	//TODO: assertEquals(true, createdAccount.receiverSignatureRequired);
    }

    @Test
    @Order(2)
    @DisplayName("Parse record files - check complex crypto create")
    void parseRecordFilesCheckCryptoCreateComplex() throws Exception {
    	// connect to database, read data, assert values
    	//TODO: Transaction Valid Duration 65
    	long nodeAccount = 3;
    	String memo = "Account Create memo";
    	String transactionType = "CRYPTOCREATEACCOUNT";
    	String result = "SUCCESS";
    	long payerAccount = 2;
    	long txFee = 13777682;
    	long initialBalance = 2000;
    	long newAccount = 1002;
    	String recordFile = files[1].getName();
    	long validStartNS = 1568033815528678000L;
    	long consensusNs = 1568033825587673001L;
    	String key = "1220019971fc0db78dec75b8c46d795294f0520fdd9177fb410db9f9376c1c3da23a";
    	String ed25519Key = "019971fc0db78dec75b8c46d795294f0520fdd9177fb410db9f9376c1c3da23a";
    			
    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newAccount, recordFile, consensusNs);
    	DBEntity createdAccount = new DBEntity();
    	createdAccount.getEntityDetails(transaction.cudEntity);

    	assertAll(
    	    	() -> assertEquals(5, DBCryptoTransfers.checkCount(transaction.consensusNs))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -13777682))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 525706))
    	    	,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 13251976))
    	    	,() -> assertEquals(10368000, createdAccount.autoRenewPeriod)
    	    	,() -> assertEquals(3, createdAccount.proxyAccountNum)
    	    	,() -> assertEquals(key, createdAccount.key)
    	    	,() -> assertEquals(ed25519Key, createdAccount.ed25519KeyHex)
    	    	,() -> assertEquals(newAccount, createdAccount.entityNum)
    	    	,() -> assertEquals(0, createdAccount.entityShard)
    	    	,() -> assertEquals(0, createdAccount.entityRealm)
    	    	,() -> assertEquals(type, createdAccount.entityType)
    	    	,() -> assertFalse(createdAccount.deleted)
        );    	
    	
    	//TODO: assertEquals(10000, createdAccount.receiveRecordThreshold);
    	//TODO: assertEquals(15000, createdAccount.sendRecordThreshold);
    	//TODO: assertEquals(true, createdAccount.receiverSignatureRequired);
    }

    @Test
    @Order(2)
    @DisplayName("Parse record files - crypto transfer no memo")
    void parseRecordFilesCheckCryptoTransferNoMemo() throws Exception {
    	// connect to database, read data, assert values
    	//TODO: Transaction Valid Duration 65
    	long nodeAccount = 3;
    	String memo = "";
    	String transactionType = "CRYPTOTRANSFER";
    	String result = "SUCCESS";
    	long payerAccount = 2;
    	long txFee = 84055;
    	long initialBalance = 0;
    	long newAccount = 0;
    	String recordFile = files[1].getName();
    	long validStartNS = 1568033816554155000L;
    	long consensusNs = 1568033826590149000L;
    			
    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newAccount, recordFile, consensusNs);
    	assertAll(
    			() -> assertEquals(5, DBCryptoTransfers.checkCount(transaction.consensusNs))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -84055))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 5126))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 78929))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 99, 200))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -200))
		);
    }

    @Test
    @Order(2)
    @DisplayName("Parse record files - crypto transfer with memo")
    void parseRecordFilesCheckCryptoTransferWithMemo() throws Exception {
    	// connect to database, read data, assert values
    	//TODO: Transaction Valid Duration 65
    	long nodeAccount = 3;
    	String memo = "Crypto Transfer memo";
    	String transactionType = "CRYPTOTRANSFER";
    	String result = "SUCCESS";
    	long payerAccount = 2;
    	long txFee = 84481;
    	long initialBalance = 0;
    	long newAccount = 0;
    	String recordFile = files[1].getName();
    	long validStartNS = 1568033817572140000L;
    	long consensusNs = 1568033827588110000L;
    			
    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, newAccount, recordFile, consensusNs);
    	assertAll(
    			() -> assertEquals(5, DBCryptoTransfers.checkCount(transaction.consensusNs))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -84481))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 5156))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 79325))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 300))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -300))
		);
    }
    @Test
    @Order(2)
    @DisplayName("Parse record files - crypto update")
    void parseRecordFilesCheckCryptoUpdate() throws Exception {
    	// connect to database, read data, assert values
    	//TODO: Transaction Valid Duration 65
    	long nodeAccount = 3;
    	String memo = "Update account memo";
    	String transactionType = "CRYPTOUPDATEACCOUNT";
    	String result = "SUCCESS";
    	long payerAccount = 2;
    	long txFee = 410373;
    	long initialBalance = 0;
    	long updAccount = 1003;
    	String recordFile = files[1].getName();
    	long validStartNS = 1568033819609412000L;
    	long consensusNs = 1568033829664529000L;
    	String key = "1220481d7771e05d9b4099f19c24d4fe361e01584d48979a8f02ff286cf36d61485e";
    	String ed25519Key = "481d7771e05d9b4099f19c24d4fe361e01584d48979a8f02ff286cf36d61485e";
    			
    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, updAccount, recordFile, consensusNs);
    	DBEntity updatedAccount = new DBEntity();
    	updatedAccount.getEntityDetails(transaction.cudEntity);

    	assertAll(
    			() -> assertEquals(3, DBCryptoTransfers.checkCount(transaction.consensusNs))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -410373))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 12047))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 398326))
    			,() -> assertEquals(432000, updatedAccount.autoRenewPeriod)
    			,() -> assertEquals(1568552229, updatedAccount.expiryTimeSeconds)
    			,() -> assertEquals(599705000, updatedAccount.expiryTimeNanos)
    			,() -> assertEquals(1568552229599705000L, updatedAccount.expiryTimeNs)
    			,() -> assertEquals(5, updatedAccount.proxyAccountNum)
    			,() -> assertEquals(key, updatedAccount.key)
    			,() -> assertEquals(ed25519Key, updatedAccount.ed25519KeyHex)
    			,() -> assertEquals(updAccount, updatedAccount.entityNum)
    			,() -> assertEquals(0, updatedAccount.entityShard)
    			,() -> assertEquals(0, updatedAccount.entityRealm)
    			,() -> assertEquals(type, updatedAccount.entityType)
    			,() -> assertFalse(updatedAccount.deleted)
		);

    	//TODO: assertEquals(10000, updatedAccount.receiveRecordThreshold);
    	//TODO: assertEquals(15000, updatedAccount.sendRecordThreshold);
    	//TODO: assertEquals(true, updatedAccount.receiverSignatureRequired);

    }
    @Test
    @Order(2)
    @DisplayName("Parse record files - crypto delete")
    void parseRecordFilesCheckCryptoDelete() throws Exception {
    	// connect to database, read data, assert values
    	//TODO: Transaction Valid Duration 65
    	long nodeAccount = 3;
    	String memo = "Delete account memo";
    	String transactionType = "CRYPTODELETE";
    	String result = "SUCCESS";
    	long payerAccount = 2;
    	long txFee = 6813352;
    	long initialBalance = 0;
    	long updAccount = 1004;
    	String recordFile = files[2].getName();
    	long validStartNS = 1568033821644081000L;
    	long consensusNs = 1568033831679017000L;
    	String key = "1220019971fc0db78dec75b8c46d795294f0520fdd9177fb410db9f9376c1c3da23a";
    	String ed25519Key = "019971fc0db78dec75b8c46d795294f0520fdd9177fb410db9f9376c1c3da23a";
    			
    	DBTransaction transaction = DBHelper.checkTransaction(validStartNS, nodeAccount, memo, transactionType, result, payerAccount, txFee, initialBalance, updAccount, recordFile, consensusNs);
    	DBEntity updatedAccount = new DBEntity();
    	updatedAccount.getEntityDetails(transaction.cudEntity);
    	assertAll(
    			() -> assertEquals(5, DBCryptoTransfers.checkCount(transaction.consensusNs))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, -6813352))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 3, 265394))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 98, 6547958))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 1004, -2000))
    			,() -> assertEquals(1, DBCryptoTransfers.checkExists(transaction.consensusNs, 2, 2000))
    			,() -> assertEquals(0, updatedAccount.expiryTimeNs)
    			,() -> assertEquals(0, updatedAccount.proxyAccountNum)
    			,() -> assertEquals(key, updatedAccount.key)
    			,() -> assertEquals(ed25519Key, updatedAccount.ed25519KeyHex)
    			,() -> assertEquals(updAccount, updatedAccount.entityNum)
    			,() -> assertEquals(0, updatedAccount.entityShard)
    			,() -> assertEquals(0, updatedAccount.entityRealm)
    			,() -> assertEquals(type, updatedAccount.entityType)
    			,() -> assertTrue(updatedAccount.deleted)
		);
    	//TODO: assertEquals(10000, updatedAccount.receiveRecordThreshold);
    	//TODO: assertEquals(15000, updatedAccount.sendRecordThreshold);
    	//TODO: assertEquals(true, updatedAccount.receiverSignatureRequired);
    }
}
