package com.hedera.recordFileLogger;

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


import com.google.protobuf.ByteString;
import com.hedera.mirror.MirrorProperties;
import com.hedera.mirror.parser.record.RecordParserProperties;
import com.hedera.recordFileLogger.RecordFileLogger;
import com.hedera.recordFileLogger.RecordFileLogger.INIT_RESULT;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

import org.junit.jupiter.api.*;
import org.springframework.test.context.jdbc.Sql;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Sql("classpath:db/scripts/cleanup.sql") 
public class RecordFileLoggerFreezeTest extends AbstractRecordFileLoggerTest {

 	private static final String memo = "File test memo";

    @BeforeEach
    void before() throws Exception {
        RecordFileLogger.parserProperties = new RecordParserProperties(new MirrorProperties());
        
		assertTrue(RecordFileLogger.start());
		assertEquals(INIT_RESULT.OK, RecordFileLogger.initFile("TestFile"));
		RecordFileLogger.parserProperties.setPersistFiles(true);
		RecordFileLogger.parserProperties.setPersistSystemFiles(true);
		RecordFileLogger.parserProperties.setPersistCryptoTransferAmounts(true);
	}

    @AfterEach
    void after() {
    	RecordFileLogger.finish();
    }

    @Test
    void freezeTest() throws Exception {

    	final Transaction transaction = freezeTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final TransactionRecord record = transactionRecord(transactionBody);

    	RecordFileLogger.storeRecord(transaction, record);
    	RecordFileLogger.completeFile("", "");

    	final com.hedera.mirror.domain.Transaction dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(4, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(0, fileDataRepository.count())

                // transaction
                ,() -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                ,() -> assertRecord(record, dbTransaction)

                // record transfer list
                ,() -> assertRecordTransfers(record)
         );
    }

    @Test
    void freezeInvalidTransactionTest() throws Exception {

    	final Transaction transaction = freezeTransaction();
    	final TransactionBody transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
    	final TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

    	RecordFileLogger.storeRecord(transaction, record);
    	RecordFileLogger.completeFile("", "");

    	final com.hedera.mirror.domain.Transaction dbTransaction = transactionRepository.findById(Utility.timeStampInNanos(record.getConsensusTimestamp())).get();

    	assertAll(
    			// row counts
                () -> assertEquals(1, recordFileRepository.count())
                ,() -> assertEquals(1, transactionRepository.count())
                ,() -> assertEquals(4, entityRepository.count())
                ,() -> assertEquals(0, contractResultRepository.count())
                ,() -> assertEquals(3, cryptoTransferRepository.count())
                ,() -> assertEquals(0, liveHashRepository.count())
                ,() -> assertEquals(0, fileDataRepository.count())

                // transaction
                ,() -> assertTransaction(transactionBody, dbTransaction)

                // record inputs
                ,() -> assertRecord(record, dbTransaction)

                // record transfer list
                ,() -> assertRecordTransfers(record)
         );
    }
    
    private TransactionRecord transactionRecord(TransactionBody transactionBody) {
    	return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum result) {
    	final TransactionRecord.Builder record = TransactionRecord.newBuilder();

    	// record
    	final Timestamp consensusTimeStamp = Utility.instantToTimestamp(Instant.now());
    	final long[] transferAccounts = {98, 2002, 3};
    	final long[] transferAmounts = {1000, -2000, 20};
    	final ResponseCodeEnum responseCode = result;
    	final TransactionReceipt.Builder receipt = TransactionReceipt.newBuilder();

    	// Build the record
    	record.setConsensusTimestamp(consensusTimeStamp);
    	record.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
    	receipt.setStatus(responseCode);

    	record.setReceipt(receipt.build());
    	record.setTransactionFee(transactionBody.getTransactionFee());
    	record.setTransactionHash(ByteString.copyFromUtf8("TransactionHash"));
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

    private Transaction freezeTransaction() {

    	final Transaction.Builder transaction = Transaction.newBuilder();
    	final FreezeTransactionBody.Builder freezeTransactionBody = FreezeTransactionBody.newBuilder();

    	// Build a transaction
    	freezeTransactionBody.setEndHour(1);
    	freezeTransactionBody.setEndMin(2);
    	freezeTransactionBody.setStartHour(3);
    	freezeTransactionBody.setStartMin(4);

    	// Transaction body
    	final TransactionBody.Builder body = defaultTransactionBodyBuilder(memo);

    	// body transaction
    	body.setFreeze(freezeTransactionBody.build());
    	transaction.setBodyBytes(body.build().toByteString());
    	transaction.setSigMap(getSigMap());

    	return transaction.build();
    }
}
