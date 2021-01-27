package com.hedera.mirror.importer.parser.record.entity;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.EntityTypeEnum;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.ScheduleSignature;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.ScheduleSignatureRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

class EntityRecordItemListenerScheduleTest extends AbstractEntityRecordItemListenerTest {

    private static final Key SCHEDULE_REF_KEY = keyFromString(
            "0a2212200aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92");
    private static final ScheduleID SCHEDULE_ID = ScheduleID.newBuilder().setShardNum(0).setRealmNum(0)
            .setScheduleNum(2).build();
    private static final ByteString SCHEDULE_CREATE_TRANSACTION_BODY = ByteString
            .copyFromUtf8("ScheduleCreate transaction body");
    private static final long CREATE_TIMESTAMP = 1L;
    private static final long UPDATE_TIMESTAMP = 5L;
    private static final long SIGN_TIMESTAMP = 10L;
    private static final long EXECUTE_TIMESTAMP = 500L;

    @Resource
    protected ScheduleRepository scheduleRepository;

    @Resource
    protected ScheduleSignatureRepository scheduleSignatureRepository;

    @Resource
    protected TransactionRepository transactionRepository;

    @BeforeEach
    void before() {
        entityProperties.getPersist().setSchedules(true);
    }

    @Test
    void scheduleCreate() throws InvalidProtocolBufferException {
        List<SignaturePair> signaturePairs = getSignaturePairs(2, true);
        insertScheduleCreate(CREATE_TIMESTAMP, signaturePairs, SCHEDULE_ID);

        // verify entity count
        Entities scheduleEntity = getScheduleEntity(SCHEDULE_ID);
        var expectedEntity = createEntity(scheduleEntity, null, null, SCHEDULE_REF_KEY, null, TRANSACTION_MEMO, 1L,
                30L, EntityTypeEnum.SCHEDULE);
        assertEquals(5, entityRepository.count()); // Node, payer, schedule and autorenew
        assertThat(scheduleEntity).isEqualTo(expectedEntity);

        // verify schedule and signatures
        assertThat(scheduleRepository.count()).isEqualTo(1L);
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, null);

        assertScheduleSignatureInRepository(CREATE_TIMESTAMP, SCHEDULE_ID, signaturePairs);

        // verify transaction
        assertTransactionInRepository(CREATE_TIMESTAMP, false);
    }

    @Test
    void scheduleUpdate() throws InvalidProtocolBufferException {
        List<SignaturePair> startingSignaturePairs = getSignaturePairs(2, true);
        insertScheduleCreate(CREATE_TIMESTAMP, startingSignaturePairs, SCHEDULE_ID);

        // update
        List<SignaturePair> additionalSignaturePairs = getSignaturePairs(3, true);
        insertScheduleUpdate(UPDATE_TIMESTAMP, startingSignaturePairs, additionalSignaturePairs, SCHEDULE_ID);

        // verify entity count
        Entities scheduleEntity = getScheduleEntity(SCHEDULE_ID);
        var expectedEntity = createEntity(scheduleEntity, null, null, SCHEDULE_REF_KEY, null, TRANSACTION_MEMO, 1L,
                30L, EntityTypeEnum.SCHEDULE);
        assertEquals(5, entityRepository.count()); // Node, payer, schedule and autorenew
        assertThat(scheduleEntity).isEqualTo(expectedEntity);

        // verify schedule
        assertThat(scheduleRepository.count()).isEqualTo(1L);
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, null);

        // verify schedule signatures
        List<SignaturePair> combinedSignaturePairs = new ArrayList<>(startingSignaturePairs);
        combinedSignaturePairs.addAll(additionalSignaturePairs);
        assertScheduleSignatureInRepository(UPDATE_TIMESTAMP, SCHEDULE_ID, combinedSignaturePairs);

        // verify transaction
        assertTransactionInRepository(UPDATE_TIMESTAMP, false);
    }

    @Test
    void scheduleSign() throws InvalidProtocolBufferException {
        List<SignaturePair> startingSignaturePairs = getSignaturePairs(2, true);
        insertScheduleCreate(CREATE_TIMESTAMP, startingSignaturePairs, SCHEDULE_ID);

        // sign
        List<SignaturePair> additionalSignaturePairs = getSignaturePairs(3, true);
        insertScheduleSign(SIGN_TIMESTAMP, additionalSignaturePairs, SCHEDULE_ID);

        // verify entity count
        Entities scheduleEntity = getScheduleEntity(SCHEDULE_ID);
        var expectedEntity = createEntity(scheduleEntity, null, null, SCHEDULE_REF_KEY, null, TRANSACTION_MEMO, 1L,
                30L, EntityTypeEnum.SCHEDULE);
        assertEquals(5, entityRepository.count()); // Node, payer, schedule and autorenew
        assertThat(scheduleEntity).isEqualTo(expectedEntity);

        // verify schedule
        assertThat(scheduleRepository.count()).isEqualTo(1L);
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, null);

        // verify schedule signatures
        assertThat(scheduleSignatureRepository.count())
                .isEqualTo(startingSignaturePairs.size() + additionalSignaturePairs.size());
        assertScheduleSignatureInRepository(CREATE_TIMESTAMP, SCHEDULE_ID, startingSignaturePairs);
        assertScheduleSignatureInRepository(SIGN_TIMESTAMP, SCHEDULE_ID, additionalSignaturePairs);

        // verify transaction
        assertTransactionInRepository(SIGN_TIMESTAMP, false);
    }

    @Test
    void scheduleSignNonEd25519Signature() {
        List<SignaturePair> signaturePairs = getSignaturePairs(2, false);
        assertThrows(InvalidDatasetException.class, () -> {
            insertScheduleSign(SIGN_TIMESTAMP, signaturePairs, SCHEDULE_ID);
        });

        // verify lack of schedule data and transaction
        assertThat(scheduleSignatureRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void scheduleSignDuplicateEd25519Signatures() throws InvalidProtocolBufferException {
        List<SignaturePair> signaturePairs = getSignaturePairs(3, true);
        List<SignaturePair> combinedSignaturePairs = new ArrayList<>(signaturePairs);
        combinedSignaturePairs.add(signaturePairs.get(0));
        combinedSignaturePairs.add(signaturePairs.get(2));

        insertScheduleSign(SIGN_TIMESTAMP, combinedSignaturePairs, SCHEDULE_ID);

        // verify lack of schedule data and transaction
        assertThat(scheduleSignatureRepository.count()).isEqualTo(signaturePairs.size());
        assertScheduleSignatureInRepository(SIGN_TIMESTAMP, SCHEDULE_ID, signaturePairs);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void scheduleExecute() throws InvalidProtocolBufferException {
        List<SignaturePair> startingSignaturePairs = getSignaturePairs(2, true);
        insertScheduleCreate(CREATE_TIMESTAMP, startingSignaturePairs, SCHEDULE_ID);

        // sign
        List<SignaturePair> additionalSignaturePairs = getSignaturePairs(3, true);
        insertScheduleSign(SIGN_TIMESTAMP, additionalSignaturePairs, SCHEDULE_ID);

        // scheduled transaction
        insertScheduledTransaction(EXECUTE_TIMESTAMP, SCHEDULE_ID);

        // verify entity count
        Entities scheduleEntity = getScheduleEntity(SCHEDULE_ID);
        var expectedEntity = createEntity(scheduleEntity, null, null, SCHEDULE_REF_KEY, null, TRANSACTION_MEMO, 1L,
                30L, EntityTypeEnum.SCHEDULE);
        assertEquals(5, entityRepository.count()); // Node, payer, schedule and autorenew
        assertThat(scheduleEntity).isEqualTo(expectedEntity);

        // verify schedule
        assertThat(scheduleRepository.count()).isEqualTo(1L);
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, EXECUTE_TIMESTAMP);

        // verify schedule signatures
        assertThat(scheduleSignatureRepository.count())
                .isEqualTo(startingSignaturePairs.size() + additionalSignaturePairs.size());
        assertScheduleSignatureInRepository(CREATE_TIMESTAMP, SCHEDULE_ID, startingSignaturePairs);
        assertScheduleSignatureInRepository(SIGN_TIMESTAMP, SCHEDULE_ID, additionalSignaturePairs);

        // verify transaction
        assertTransactionInRepository(EXECUTE_TIMESTAMP, true);
    }

    private Transaction scheduleCreateTransaction(List<SignaturePair> originalSignaturePairs,
                                                  List<SignaturePair> newSignaturePairs) {
        return buildTransaction(builder -> {
            builder.getScheduleCreateBuilder()
                    .setAdminKey(Key.newBuilder().setEd25519(ByteString.copyFromUtf8(KEY)).build())
                    .setPayerAccountID(PAYER)
                    .setSigMap(getSigMap(originalSignaturePairs, newSignaturePairs))
                    .setTransactionBody(SCHEDULE_CREATE_TRANSACTION_BODY);
        });
    }

    private Transaction scheduleSignTransaction(ScheduleID scheduleID, List<SignaturePair> signaturePairs) {
        return buildTransaction(builder -> {
            builder.getScheduleSignBuilder()
                    .setScheduleID(scheduleID)
                    .setSigMap(getSigMap(signaturePairs, Collections.EMPTY_LIST));
        });
    }

    private Transaction scheduledTransaction() {
        return buildTransaction(builder -> {
            builder.getCryptoTransferBuilder().getTransfersBuilder()
                    .addAccountAmounts(accountAmount(PAYER.getAccountNum(), 1000))
                    .addAccountAmounts(accountAmount(NODE.getAccountNum(), 2000));
        });
    }

    private SignatureMap getSigMap(List<SignaturePair> originalSignaturePairs, List<SignaturePair> newSignaturePairs) {
        SignatureMap.Builder signatureBuilder = SignatureMap.newBuilder();
        // add existing signatures
        originalSignaturePairs.forEach(signaturePair -> signatureBuilder.addSigPair(signaturePair));

        // add new signatures
        newSignaturePairs.forEach(signaturePair -> signatureBuilder.addSigPair(signaturePair));

        return signatureBuilder.build();
    }

    private List<SignaturePair> getSignaturePairs(int signatureCount, boolean isEd25519) {
        String salt = RandomStringUtils.randomAlphabetic(5);

        List<SignaturePair> signaturePairs = new ArrayList<>();
        for (int i = 0; i < signatureCount; i++) {
            SignaturePair.Builder signaturePaiBuilder = SignaturePair.newBuilder();
            signaturePaiBuilder.setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-" + i + salt));

            ByteString byteString = ByteString.copyFromUtf8("Ed25519-" + i + salt);
            if (isEd25519) {
                signaturePaiBuilder.setEd25519(byteString);
            } else {
                signaturePaiBuilder.setRSA3072(byteString);
            }

            signaturePairs.add(signaturePaiBuilder.build());
        }

        return signaturePairs;
    }

    private TransactionRecord createTransactionRecord(long consensusTimestamp, ScheduleID scheduleID,
                                                      TransactionBody transactionBody, ResponseCodeEnum responseCode,
                                                      boolean scheduledTransaction) {
        var receipt = TransactionReceipt.newBuilder()
                .setStatus(responseCode)
                .setScheduleID(scheduleID);

        return buildTransactionRecord(recordBuilder -> {
                    recordBuilder
                            .setReceipt(receipt)
                            .setConsensusTimestamp(TestUtils.toTimestamp(consensusTimestamp));

                    if (scheduledTransaction) {
                        recordBuilder.setScheduleRef(scheduleID);
                    }

                    recordBuilder.getReceiptBuilder().setAccountID(PAYER);
                },
                transactionBody, responseCode.getNumber());
    }

    private Entities getScheduleEntity(ScheduleID scheduleID) {
        return getEntity(EntityId.of(scheduleID).getId());
    }

    private void insertScheduleCreate(long createdTimestamp, List<SignaturePair> signaturePairs,
                                      ScheduleID scheduleID) throws InvalidProtocolBufferException {
        insertScheduleCreateTransaction(createdTimestamp, signaturePairs, Collections.EMPTY_LIST, scheduleID);
    }

    private void insertScheduleUpdate(long createdTimestamp, List<SignaturePair> initialSignaturePairs,
                                      List<SignaturePair> newSignaturePairs, ScheduleID scheduleID) throws InvalidProtocolBufferException {
        insertScheduleCreateTransaction(createdTimestamp, initialSignaturePairs, newSignaturePairs, scheduleID);
    }

    private void insertScheduleCreateTransaction(long createdTimestamp,
                                                 List<SignaturePair> initialSignaturePairs,
                                                 List<SignaturePair> newSignaturePairs,
                                                 ScheduleID scheduleID) throws InvalidProtocolBufferException {
        Transaction createTransaction = scheduleCreateTransaction(initialSignaturePairs, newSignaturePairs);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        var createTransactionRecord = createTransactionRecord(createdTimestamp, scheduleID, createTransactionBody,
                ResponseCodeEnum.SUCCESS, false);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createTransactionRecord));
    }

    private void insertScheduleSign(long signTimestamp, List<SignaturePair> signaturePairs,
                                    ScheduleID scheduleID) throws InvalidProtocolBufferException {
        Transaction signTransaction = scheduleSignTransaction(scheduleID, signaturePairs);
        TransactionBody signTransactionBody = getTransactionBody(signTransaction);
        var signTransactionRecord = createTransactionRecord(signTimestamp, scheduleID, signTransactionBody,
                ResponseCodeEnum.SUCCESS, false);

        parseRecordItemAndCommit(new RecordItem(signTransaction, signTransactionRecord));
    }

    private void insertScheduledTransaction(long signTimestamp, ScheduleID scheduleID) throws InvalidProtocolBufferException {
        Transaction scheduledTransaction = scheduledTransaction();
        TransactionBody scheduledTransactionBody = getTransactionBody(scheduledTransaction);
        var scheduledTransactionRecord = createTransactionRecord(signTimestamp, scheduleID, scheduledTransactionBody,
                ResponseCodeEnum.SUCCESS, true);

        parseRecordItemAndCommit(new RecordItem(scheduledTransaction, scheduledTransactionRecord));
    }

    private void assertScheduleInRepository(ScheduleID scheduleID, long createdTimestamp,
                                            Long executedTimestamp) {
        assertThat(scheduleRepository.findById(createdTimestamp)).get()
                .returns(createdTimestamp, from(Schedule::getConsensusTimestamp))
                .returns(executedTimestamp, from(Schedule::getExecutedTimestamp))
                .returns(EntityId.of(scheduleID), from(Schedule::getScheduleId))
                .returns(EntityId.of(PAYER), from(Schedule::getCreatorAccountId))
                .returns(EntityId.of(PAYER), from(Schedule::getPayerAccountId))
                .returns(SCHEDULE_CREATE_TRANSACTION_BODY.toByteArray(), from(Schedule::getTransactionBody));
    }

    private void assertScheduleSignatureInRepository(long createdTimestamp, ScheduleID scheduleID,
                                                     List<SignaturePair> signaturePairs) {
        List<ScheduleSignature> scheduleSignatures = (List<ScheduleSignature>) scheduleSignatureRepository.findAll();
        assertThat(scheduleSignatures).isNotNull();

        // repository should contain at least signaturePairs count of signatures
        assertThat(scheduleSignatures.size()).isGreaterThanOrEqualTo(signaturePairs.size());

        signaturePairs.forEach(signaturePair -> {
            assertThat(scheduleSignatureRepository.findById(new ScheduleSignature.Id(
                    createdTimestamp,
                    signaturePair.getPubKeyPrefix().toByteArray()))).get()
                    .returns(EntityId.of(scheduleID), from(ScheduleSignature::getScheduleId))
                    .returns(signaturePair.getEd25519().toByteArray(), from(ScheduleSignature::getSignature));
        });
    }

    private void assertTransactionInRepository(long consensusTimestamp, boolean scheduled) {
        assertThat(transactionRepository.findById(consensusTimestamp)).get()
                .returns(scheduled, from(com.hedera.mirror.importer.domain.Transaction::isScheduled));
    }
}
