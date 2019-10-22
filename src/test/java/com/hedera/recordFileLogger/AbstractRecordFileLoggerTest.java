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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Optional;

import javax.annotation.Resource;

import com.hedera.mirror.parser.record.RecordParserProperties;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;

import com.google.protobuf.ByteString;
import com.hedera.IntegrationTest;
import com.hedera.mirror.domain.Entities;
import com.hedera.mirror.domain.TransactionResult;
import com.hedera.mirror.repository.ContractResultRepository;
import com.hedera.mirror.repository.CryptoTransferRepository;
import com.hedera.mirror.repository.EntityRepository;
import com.hedera.mirror.repository.EntityTypeRepository;
import com.hedera.mirror.repository.FileDataRepository;
import com.hedera.mirror.repository.LiveHashRepository;
import com.hedera.mirror.repository.RecordFileRepository;
import com.hedera.mirror.repository.TransactionRepository;
import com.hedera.mirror.repository.TransactionResultRepository;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;

public class AbstractRecordFileLoggerTest extends IntegrationTest {

    @Resource
    protected TransactionRepository transactionRepository;
    @Resource
    protected EntityRepository entityRepository;
    @Resource
    protected ContractResultRepository contractResultRepository;
    @Resource
    protected RecordFileRepository recordFileRepository;
    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;
    @Resource
    protected LiveHashRepository liveHashRepository;
    @Resource
    protected FileDataRepository fileDataRepository;
    @Resource
    protected TransactionResultRepository transactionResultRepository;
    @Resource
    protected EntityTypeRepository entityTypeRepository;

    @Resource
    protected RecordParserProperties parserProperties;

    protected final void assertAccount(AccountID accountId, Entities dbEntity) {
        assertThat(accountId)
            .isNotEqualTo(AccountID.getDefaultInstance())
            .extracting(AccountID::getShardNum, AccountID::getRealmNum, AccountID::getAccountNum)
            .containsExactly(dbEntity.getEntityShard(), dbEntity.getEntityRealm(), dbEntity.getEntityNum());
        assertThat(dbEntity.getEntityTypeId())
        	.isEqualTo(entityTypeRepository.findByName("account").get().getId());
    }
    protected final void assertFile(FileID fileId, Entities dbEntity) {
        assertThat(fileId)
            .isNotEqualTo(FileID.getDefaultInstance())
            .extracting(FileID::getShardNum, FileID::getRealmNum, FileID::getFileNum)
            .containsExactly(dbEntity.getEntityShard(), dbEntity.getEntityRealm(), dbEntity.getEntityNum());
        assertThat(dbEntity.getEntityTypeId())
        	.isEqualTo(entityTypeRepository.findByName("file").get().getId());
    }
    protected final void assertRecordTransfers(TransactionRecord record) {
    	final TransferList transferList = record.getTransferList();
    	for (AccountAmount accountAmount : transferList.getAccountAmountsList()) {
    		AccountID xferAccountId = accountAmount.getAccountID();
    		Optional<Entities> accountId = entityRepository.findByPrimaryKey(xferAccountId.getShardNum(), xferAccountId.getRealmNum(), xferAccountId.getAccountNum());
    		assertEquals(accountAmount.getAmount(), cryptoTransferRepository.findByConsensusTimestampAndAccountId(Utility.timeStampInNanos(record.getConsensusTimestamp()), accountId.get().getId()).get().getAmount());
    	}
    }
    protected final void assertRecord(TransactionRecord record, com.hedera.mirror.domain.Transaction dbTransaction) {
    	final Entities dbPayerEntity = entityRepository.findById(dbTransaction.getPayerAccountId()).get();
    	final TransactionResult dbResult = transactionResultRepository.findById(dbTransaction.getResultId()).get();
        // record inputs
        assertEquals(Utility.timeStampInNanos(record.getConsensusTimestamp()), dbTransaction.getConsensusNs());
        assertEquals(record.getTransactionFee(), dbTransaction.getChargedTxFee());
        // payer
        assertAccount(record.getTransactionID().getAccountID(), dbPayerEntity);
        // transaction id
        assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()), dbTransaction.getValidStartNs());
        // receipt
        assertEquals(record.getReceipt().getStatusValue(), dbResult.getProtobufId());
        assertEquals(record.getReceipt().getStatus().getValueDescriptor().getName(), dbResult.getResult());

    }
    protected final void assertTransaction(TransactionBody transactionBody, com.hedera.mirror.domain.Transaction dbTransaction) {

    	final Entities dbNodeEntity = entityRepository.findById(dbTransaction.getNodeAccountId()).get();
    	final Entities dbPayerEntity = entityRepository.findById(dbTransaction.getPayerAccountId()).get();

    	assertAll(
            () -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.getMemo())
                ,() -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)
                ,() -> assertAccount(transactionBody.getTransactionID().getAccountID(), dbPayerEntity)
                ,() -> assertEquals(Utility.timeStampInNanos(transactionBody.getTransactionID().getTransactionValidStart()), dbTransaction.getValidStartNs())
         );
    }
    protected final SignatureMap getSigMap() {
    	final String key1 = "11111111111111111111c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
    	final String signature1 = "Signature 1 here";
    	final String key2 = "22222222222222222222c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
    	final String signature2 = "Signature 2 here";

    	SignatureMap.Builder sigMap = SignatureMap.newBuilder();
    	SignaturePair.Builder sigPair = SignaturePair.newBuilder();
    	sigPair.setEd25519(ByteString.copyFromUtf8(signature1));
    	sigPair.setPubKeyPrefix(ByteString.copyFromUtf8(key1));

    	sigMap.addSigPair(sigPair);

    	sigPair = SignaturePair.newBuilder();
    	sigPair.setEd25519(ByteString.copyFromUtf8(signature2));
    	sigPair.setPubKeyPrefix(ByteString.copyFromUtf8(key2));

    	sigMap.addSigPair(sigPair);

    	return sigMap.build();
    }

    protected final Key keyFromString(String key) {
    	return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build();
    }
    protected final Builder defaultTransactionBodyBuilder(String memo) {

        final long validDuration = 120;
        final AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2).build();
        final long txFee = 53968962L;
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
