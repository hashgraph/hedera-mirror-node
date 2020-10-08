package com.hedera.mirror.importer.parser.record.entity;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;
import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import com.hedera.mirror.importer.IntegrationTest;
import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.CryptoTransfer;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.domain.StreamFileData;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.RecordStreamFileListener;
import com.hedera.mirror.importer.repository.ContractResultRepository;
import com.hedera.mirror.importer.repository.CryptoTransferRepository;
import com.hedera.mirror.importer.repository.EntityRepository;
import com.hedera.mirror.importer.repository.FileDataRepository;
import com.hedera.mirror.importer.repository.LiveHashRepository;
import com.hedera.mirror.importer.repository.NonFeeTransferRepository;
import com.hedera.mirror.importer.repository.RecordFileRepository;
import com.hedera.mirror.importer.repository.TopicMessageRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;
import com.hedera.mirror.importer.util.EntityIdEndec;
import com.hedera.mirror.importer.util.Utility;

public class AbstractEntityRecordItemListenerTest extends IntegrationTest {
    protected static final String KEY = "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110fff";
    protected static final String KEY2 = "0a3312200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92";
    protected static final AccountID PAYER =
            AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2002).build();
    static final String TRANSACTION_MEMO = "transaction memo";

    @Resource
    protected TransactionRepository transactionRepository;
    @Resource
    protected EntityRepository entityRepository;
    @Resource
    protected ContractResultRepository contractResultRepository;
    @Resource
    protected CryptoTransferRepository cryptoTransferRepository;
    @Resource
    protected LiveHashRepository liveHashRepository;
    @Resource
    protected FileDataRepository fileDataRepository;
    @Resource
    protected TopicMessageRepository topicMessageRepository;
    @Resource
    protected NonFeeTransferRepository nonFeeTransferRepository;

    @Resource
    protected EntityRecordItemListener entityRecordItemListener;

    @Resource
    protected RecordParserProperties parserProperties;

    @Resource
    protected EntityProperties entityProperties;

    @Resource
    protected RecordStreamFileListener recordStreamFileListener;

    @Resource
    protected RecordFileRepository recordFileRepository;

    protected static SignatureMap getSigMap() {
        String key1 = "11111111111111111111c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
        String signature1 = "Signature 1 here";
        String key2 = "22222222222222222222c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e91";
        String signature2 = "Signature 2 here";

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

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        System.out.println("Before test: " + testInfo.getTestMethod().get().getName());
    }

    protected static Key keyFromString(String key) {
        return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(key)).build();
    }

    protected final void assertAccount(AccountID accountId, Entities dbEntity) {
        assertThat(accountId)
                .isNotEqualTo(AccountID.getDefaultInstance())
                .extracting(AccountID::getShardNum, AccountID::getRealmNum, AccountID::getAccountNum)
                .containsExactly(dbEntity.getEntityShard(), dbEntity.getEntityRealm(), dbEntity.getEntityNum());
        assertThat(dbEntity.getEntityTypeId())
                .isEqualTo(EntityTypeEnum.ACCOUNT.getId());
    }

    protected final void assertFile(FileID fileId, Entities dbEntity) {
        assertThat(fileId)
                .isNotEqualTo(FileID.getDefaultInstance())
                .extracting(FileID::getShardNum, FileID::getRealmNum, FileID::getFileNum)
                .containsExactly(dbEntity.getEntityShard(), dbEntity.getEntityRealm(), dbEntity.getEntityNum());
        assertThat(dbEntity.getEntityTypeId())
                .isEqualTo(EntityTypeEnum.FILE.getId());
    }

    protected final void assertContract(ContractID contractId, Entities dbEntity) {
        assertThat(contractId)
                .isNotEqualTo(ContractID.getDefaultInstance())
                .extracting(ContractID::getShardNum, ContractID::getRealmNum, ContractID::getContractNum)
                .containsExactly(dbEntity.getEntityShard(), dbEntity.getEntityRealm(), dbEntity.getEntityNum());
        assertThat(dbEntity.getEntityTypeId())
                .isEqualTo(EntityTypeEnum.CONTRACT.getId());
    }

    protected void parseRecordItemAndCommit(RecordItem recordItem) {
        String fileName = UUID.randomUUID().toString();
        EntityId nodeAccountId = EntityId.of(TestUtils.toAccountId("0.0.3"));
        RecordFile recordFile = new RecordFile(0L, 0L, null, fileName, 0L, 0L, UUID.randomUUID()
                .toString(), "", nodeAccountId, 0L, 0);
        recordFileRepository.save(recordFile);
        recordStreamFileListener.onStart(new StreamFileData(fileName, null)); // open connection
        entityRecordItemListener.onItem(recordItem);
        // commit, close connection
        recordStreamFileListener.onEnd(recordFile);
    }

    protected void assertRecordTransfers(TransactionRecord record) {
        long consensusTimestamp = Utility.timeStampInNanos(record.getConsensusTimestamp());
        if (entityProperties.getPersist().isCryptoTransferAmounts()) {
            TransferList transferList = record.getTransferList();
            for (AccountAmount accountAmount : transferList.getAccountAmountsList()) {
                EntityId account = EntityId.of(accountAmount.getAccountID());
                assertThat(cryptoTransferRepository
                        .findById(new CryptoTransfer.Id(accountAmount.getAmount(), consensusTimestamp, account)))
                        .isPresent();
            }
        } else {
            assertThat(cryptoTransferRepository.count()).isEqualTo(0L);
        }
    }

    protected void assertTransactionAndRecord(TransactionBody transactionBody, TransactionRecord record) {
        var dbTransaction = getDbTransaction(record.getConsensusTimestamp());
        assertTransaction(transactionBody, dbTransaction);
        assertRecord(record, dbTransaction);
    }

    private void assertRecord(TransactionRecord record, Transaction dbTransaction) {
        // record inputs
        assertEquals(record.getTransactionFee(), dbTransaction.getChargedTxFee());
        // transaction id
        assertEquals(Utility.timeStampInNanos(record.getTransactionID().getTransactionValidStart()),
                dbTransaction.getValidStartNs());
        // receipt
        assertEquals(record.getReceipt().getStatusValue(), dbTransaction.getResult());
        assertArrayEquals(record.getTransactionHash().toByteArray(), dbTransaction.getTransactionHash());
        // assert crypto transfer list
        assertRecordTransfers(record);
    }

    private void assertTransaction(TransactionBody transactionBody, Transaction dbTransaction) {
        Entities dbNodeEntity = getEntity(dbTransaction.getNodeAccountId());
        Entities dbPayerEntity = getEntity(dbTransaction.getPayerAccountId());

        assertAll(
                () -> assertArrayEquals(transactionBody.getMemoBytes().toByteArray(), dbTransaction.getMemo())
                , () -> assertAccount(transactionBody.getNodeAccountID(), dbNodeEntity)
                , () -> assertAccount(transactionBody.getTransactionID().getAccountID(), dbPayerEntity)
                , () -> assertEquals(
                        Utility.timeStampInNanos(transactionBody.getTransactionID().getTransactionValidStart()),
                        dbTransaction.getValidStartNs())
                , () -> assertEquals(transactionBody.getTransactionValidDuration().getSeconds(),
                        dbTransaction.getValidDurationSeconds())
                , () -> assertEquals(transactionBody.getTransactionFee(), dbTransaction.getMaxFee())
                // By default the raw bytes are not stored
                , () -> assertEquals(null, dbTransaction.getTransactionBytes())
        );
    }

    private static Builder defaultTransactionBodyBuilder() {
        TransactionBody.Builder body = TransactionBody.newBuilder();
        body.setTransactionFee(100L);
        body.setMemo(TRANSACTION_MEMO);
        body.setNodeAccountID(AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(3).build());
        body.setTransactionID(Utility.getTransactionId(PAYER));
        body.setTransactionValidDuration(Duration.newBuilder().setSeconds(120).build());
        return body;
    }

    protected com.hederahashgraph.api.proto.java.Transaction buildTransaction(Consumer<Builder> customBuilder) {
        TransactionBody.Builder bodyBuilder = defaultTransactionBodyBuilder();
        customBuilder.accept(bodyBuilder);

        return com.hederahashgraph.api.proto.java.Transaction.newBuilder()
                .setSignedTransactionBytes(SignedTransaction.newBuilder()
                        .setBodyBytes(bodyBuilder.build().toByteString())
                        .setSigMap(getSigMap())
                        .build().toByteString())
                .build();
    }

    protected TransactionRecord buildTransactionRecord(
            Consumer<TransactionRecord.Builder> customBuilder, TransactionBody transactionBody, int status) {
        TransactionRecord.Builder recordBuilder = TransactionRecord.newBuilder();
        recordBuilder.setConsensusTimestamp(Utility.instantToTimestamp(Instant.now()));
        recordBuilder.setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()));
        recordBuilder.setTransactionFee(transactionBody.getTransactionFee());
        recordBuilder.setTransactionHash(ByteString.copyFromUtf8("TransactionHash"));
        recordBuilder.setTransactionID(transactionBody.getTransactionID());
        recordBuilder.getReceiptBuilder().setStatusValue(status);

        // Give from payer to treasury and node
        long[] transferAccounts = {PAYER.getAccountNum(), 98, 3};
        long[] transferAmounts = {-2000, 1000, 1000};
        TransferList.Builder transferList = recordBuilder.getTransferListBuilder();
        for (int i = 0; i < transferAccounts.length; i++) {
            // Irrespective of transaction success, node and network fees are present.
            transferList.addAccountAmounts(accountAmount(transferAccounts[i], transferAmounts[i]));
        }
        if (transactionBody.hasCryptoTransfer() && status == ResponseCodeEnum.SUCCESS.getNumber()) {
            for (var aa : transactionBody.getCryptoTransfer().getTransfers().getAccountAmountsList()) {
                transferList.addAccountAmounts(aa);
            }
        }
        customBuilder.accept(recordBuilder);
        return recordBuilder.build();
    }

    protected com.hedera.mirror.importer.domain.Transaction getDbTransaction(Timestamp consensusTimestamp) {
        return transactionRepository.findById(Utility.timeStampInNanos(consensusTimestamp)).get();
    }

    protected Entities getTransactionEntity(Timestamp consensusTimestamp) {
        var transaction = transactionRepository.findById(Utility.timeStampInNanos(consensusTimestamp)).get();
        return getEntity(transaction.getEntityId());
    }

    protected Entities getEntity(long entityId) {
        return entityRepository.findById(entityId).get();
    }

    protected Entities getEntity(EntityId entityId) {
        return getEntity(entityId.getId());
    }

    protected Entities getEntity(long shard, long realm, long num) {
        return getEntity(EntityIdEndec.encode(shard, realm, num));
    }

    protected AccountAmount.Builder accountAmount(long accountNum, long amount) {
        return AccountAmount.newBuilder().setAccountID(AccountID.newBuilder().setAccountNum(accountNum))
                .setAmount(amount);
    }

    protected TransactionBody getTransactionBody(com.hederahashgraph.api.proto.java.Transaction transaction) throws InvalidProtocolBufferException {
        return TransactionBody.parseFrom(
                SignedTransaction.parseFrom(transaction.getSignedTransactionBytes()).getBodyBytes());
    }

    protected Entities createEntity(Entities entities, Long expirationTimeSeconds, Integer expirationTimeNanos,
                                    Key adminKey, Key submitKey, String memo, Long autoRenewAccountNum,
                                    Long autoRenewPeriod, EntityTypeEnum entityTypeEnum) {
        if (autoRenewAccountNum != null) {
            var autoRenewAccount = EntityId.of(0L, 0L, autoRenewAccountNum, EntityTypeEnum.ACCOUNT);
            entityRepository.findById(autoRenewAccount.getId())
                    .orElse(entityRepository.save(autoRenewAccount.toEntity()));
            entities.setAutoRenewAccountId(autoRenewAccount);
        }
        if (autoRenewPeriod != null) {
            entities.setAutoRenewPeriod(autoRenewPeriod);
        }
        if (expirationTimeSeconds != null && expirationTimeNanos != null) {
            entities.setExpiryTimeNs(Utility.convertToNanosMax(expirationTimeSeconds, expirationTimeNanos));
        }
        if (null != adminKey) {
            entities.setKey(adminKey.toByteArray());
        }
        if (null != submitKey) {
            entities.setSubmitKey(submitKey.toByteArray());
        }
        entities.setMemo(memo);
        entities.setEntityTypeId(entityTypeEnum.getId());
        return entities;
    }

    protected void assertTransactionInRepository(
            ResponseCodeEnum responseCode, long consensusTimestamp, Long entityId) {
        var transaction = transactionRepository.findById(consensusTimestamp).get();
        assertThat(transaction)
                .returns(responseCode.getNumber(), from(Transaction::getResult))
                .returns(TRANSACTION_MEMO.getBytes(), from(Transaction::getMemo));
        if (entityId != null) {
            assertThat(transaction)
                    .returns(entityId, t -> t.getEntityId().getId());
        }
    }
}
