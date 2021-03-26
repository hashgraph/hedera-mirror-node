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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Resource;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Schedule;
import com.hedera.mirror.importer.domain.ScheduleSignature;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.ScheduleRepository;
import com.hedera.mirror.importer.repository.ScheduleSignatureRepository;
import com.hedera.mirror.importer.repository.TransactionRepository;

class EntityRecordItemListenerScheduleTest extends AbstractEntityRecordItemListenerTest {

    private static final long CREATE_TIMESTAMP = 1L;
    private static final long EXECUTE_TIMESTAMP = 500L;
    private static final String SCHEDULE_CREATE_MEMO = "ScheduleCreate memo";
    private static final SchedulableTransactionBody SCHEDULED_TRANSACTION_BODY = SchedulableTransactionBody
            .getDefaultInstance();
    private static final ScheduleID SCHEDULE_ID = ScheduleID.newBuilder().setShardNum(0).setRealmNum(0)
            .setScheduleNum(2).build();
    private static final Key SCHEDULE_REF_KEY = keyFromString(KEY);
    private static final long SIGN_TIMESTAMP = 10L;

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

    @ParameterizedTest(name = "{2}")
    @MethodSource("provideScheduleCreatePayer")
    void scheduleCreate(AccountID payer, AccountID expectedPayer, String name) throws InvalidProtocolBufferException {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, payer, SCHEDULE_ID);

        // verify entity count
        Entities expected = createEntity(EntityId.of(SCHEDULE_ID), SCHEDULE_REF_KEY, null,
                null, false, null, SCHEDULE_CREATE_MEMO, null);
        int expectedEntityCount = 4; // node, payer, schedule and autorenew
        if (!expectedPayer.equals(PAYER)) {
            expectedEntityCount += 1;
        }
        assertEquals(expectedEntityCount, entityRepository.count());
        assertEntity(expected);

        // verify schedule and signatures
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, expectedPayer, null);

        assertScheduleSignatureInRepository(Collections.emptyList());

        // verify transaction
        assertTransactionInRepository(CREATE_TIMESTAMP, false, ResponseCodeEnum.SUCCESS);
    }

    @Test
    void scheduleSign() throws InvalidProtocolBufferException {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, SCHEDULE_ID);

        // sign
        SignatureMap signatureMap = getSigMap(3, true);
        insertScheduleSign(SIGN_TIMESTAMP, signatureMap, SCHEDULE_ID);

        // verify entity count
        Entities expected = createEntity(EntityId.of(SCHEDULE_ID), SCHEDULE_REF_KEY, null,
                null, false, null, SCHEDULE_CREATE_MEMO, null);
        assertEquals(4, entityRepository.count()); // Node, payer, schedule and autorenew
        assertEntity(expected);

        // verify schedule
        assertThat(scheduleRepository.count()).isEqualTo(1L);
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, PAYER, null);

        // verify schedule signatures
        assertScheduleSignatureInRepository(toScheduleSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID, signatureMap));

        // verify transaction
        assertTransactionInRepository(SIGN_TIMESTAMP, false, ResponseCodeEnum.SUCCESS);
    }

    @Test
    void scheduleSignTwoBatches() throws InvalidProtocolBufferException {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, SCHEDULE_ID);

        // first sign
        SignatureMap firstSignatureMap = getSigMap(2, true);
        insertScheduleSign(SIGN_TIMESTAMP, firstSignatureMap, SCHEDULE_ID);

        // verify schedule signatures
        List<ScheduleSignature> firstScheduleSignatures = toScheduleSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID,
                firstSignatureMap);
        assertScheduleSignatureInRepository(toScheduleSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID, firstSignatureMap));

        // second sign
        long timestamp = SIGN_TIMESTAMP + 10;
        SignatureMap secondSignatureMap = getSigMap(3, true);
        insertScheduleSign(timestamp, secondSignatureMap, SCHEDULE_ID);

        List<ScheduleSignature> combinedScheduleSignatures = new ArrayList<>(firstScheduleSignatures);
        combinedScheduleSignatures.addAll(toScheduleSignatureList(timestamp, SCHEDULE_ID, secondSignatureMap));
        assertScheduleSignatureInRepository(combinedScheduleSignatures);

        // verify entity count
        Entities expected = createEntity(EntityId.of(SCHEDULE_ID), SCHEDULE_REF_KEY, null,
                null, false, null, SCHEDULE_CREATE_MEMO, null);
        assertEquals(4, entityRepository.count()); // Node, payer, schedule and autorenew
        assertEntity(expected);

        // verify schedule
        assertThat(scheduleRepository.count()).isEqualTo(1L);
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, PAYER, null);

        // verify transaction
        assertTransactionInRepository(SIGN_TIMESTAMP, false, ResponseCodeEnum.SUCCESS);
    }

    @Test
    void scheduleSignNonEd25519Signature() {
        SignatureMap signatureMap = getSigMap(2, false);
        assertThrows(InvalidDatasetException.class,
                () -> insertScheduleSign(SIGN_TIMESTAMP, signatureMap, SCHEDULE_ID));

        // verify lack of schedule data and transaction
        assertThat(scheduleSignatureRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void scheduleSignDuplicateEd25519Signatures() throws InvalidProtocolBufferException {
        SignatureMap signatureMap = getSigMap(3, true);
        SignaturePair first = signatureMap.getSigPair(0);
        SignaturePair third = signatureMap.getSigPair(2);
        SignatureMap signatureMapWithDuplicate = signatureMap.toBuilder()
                .addSigPair(first)
                .addSigPair(third)
                .build();

        insertScheduleSign(SIGN_TIMESTAMP, signatureMapWithDuplicate, SCHEDULE_ID);

        // verify lack of schedule data and transaction
        assertScheduleSignatureInRepository(toScheduleSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID, signatureMap));
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void scheduleExecuteOnSuccess() throws InvalidProtocolBufferException {
        scheduleExecute(ResponseCodeEnum.SUCCESS);
    }

    @Test
    void scheduleExecuteOnFailure() throws InvalidProtocolBufferException {
        scheduleExecute(ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID);
    }

    void scheduleExecute(ResponseCodeEnum responseCodeEnum) throws InvalidProtocolBufferException {
        insertScheduleCreateTransaction(CREATE_TIMESTAMP, null, SCHEDULE_ID);

        // sign
        SignatureMap signatureMap = getSigMap(3, true);
        insertScheduleSign(SIGN_TIMESTAMP, signatureMap, SCHEDULE_ID);

        // scheduled transaction
        insertScheduledTransaction(EXECUTE_TIMESTAMP, SCHEDULE_ID, responseCodeEnum);

        // verify entity count
        Entities expected = createEntity(EntityId.of(SCHEDULE_ID), SCHEDULE_REF_KEY, null,
                null, false, null, SCHEDULE_CREATE_MEMO, null);
        assertEquals(4, entityRepository.count()); // Node, payer, schedule and autorenew
        assertEntity(expected);

        // verify schedule
        assertScheduleInRepository(SCHEDULE_ID, CREATE_TIMESTAMP, PAYER, EXECUTE_TIMESTAMP);

        // verify schedule signatures
        assertScheduleSignatureInRepository(toScheduleSignatureList(SIGN_TIMESTAMP, SCHEDULE_ID, signatureMap));

        // verify transaction
        assertTransactionInRepository(EXECUTE_TIMESTAMP, true, responseCodeEnum);
    }

    private static Stream<Arguments> provideScheduleCreatePayer() {
        return Stream.of(
                Arguments.of(null, PAYER, "no payer expect same as creator"),
                Arguments.of(PAYER, PAYER, "payer set to creator"),
                Arguments.of(PAYER2, PAYER2, "payer different than creator")
        );
    }

    private Transaction scheduleCreateTransaction(AccountID payer) {
        return buildTransaction(builder -> {
            ScheduleCreateTransactionBody.Builder scheduleCreateBuilder = builder.getScheduleCreateBuilder();
            scheduleCreateBuilder
                    .setAdminKey(SCHEDULE_REF_KEY)
                    .setMemo(SCHEDULE_CREATE_MEMO)
                    .setScheduledTransactionBody(SCHEDULED_TRANSACTION_BODY);
            if (payer != null) {
                scheduleCreateBuilder.setPayerAccountID(payer);
            } else {
                scheduleCreateBuilder.clearPayerAccountID();
            }
        });
    }

    private Transaction scheduleSignTransaction(ScheduleID scheduleID, SignatureMap signatureMap) {
        return buildTransaction(builder -> builder.getScheduleSignBuilder().setScheduleID(scheduleID), signatureMap);
    }

    private Transaction scheduledTransaction() {
        return buildTransaction(builder -> builder.getCryptoTransferBuilder().getTransfersBuilder()
                .addAccountAmounts(accountAmount(PAYER.getAccountNum(), 1000))
                .addAccountAmounts(accountAmount(NODE.getAccountNum(), 2000)));
    }

    private SignatureMap getSigMap(int signatureCount, boolean isEd25519) {
        SignatureMap.Builder builder = SignatureMap.newBuilder();
        String salt = RandomStringUtils.randomAlphabetic(5);

        for (int i = 0; i < signatureCount; i++) {
            SignaturePair.Builder signaturePairBuilder = SignaturePair.newBuilder();
            signaturePairBuilder.setPubKeyPrefix(ByteString.copyFromUtf8("PubKeyPrefix-" + i + salt));

            ByteString byteString = ByteString.copyFromUtf8("Ed25519-" + i + salt);
            if (isEd25519) {
                signaturePairBuilder.setEd25519(byteString);
            } else {
                signaturePairBuilder.setRSA3072(byteString);
            }

            builder.addSigPair(signaturePairBuilder.build());
        }

        return builder.build();
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

    private void insertScheduleCreateTransaction(long createdTimestamp,
                                                 AccountID payer,
                                                 ScheduleID scheduleID) throws InvalidProtocolBufferException {
        Transaction createTransaction = scheduleCreateTransaction(payer);
        TransactionBody createTransactionBody = getTransactionBody(createTransaction);
        var createTransactionRecord = createTransactionRecord(createdTimestamp, scheduleID, createTransactionBody,
                ResponseCodeEnum.SUCCESS, false);

        parseRecordItemAndCommit(new RecordItem(createTransaction, createTransactionRecord));
    }

    private void insertScheduleSign(long signTimestamp, SignatureMap signatureMap,
                                    ScheduleID scheduleID) throws InvalidProtocolBufferException {
        Transaction signTransaction = scheduleSignTransaction(scheduleID, signatureMap);
        TransactionBody signTransactionBody = getTransactionBody(signTransaction);
        var signTransactionRecord = createTransactionRecord(signTimestamp, scheduleID, signTransactionBody,
                ResponseCodeEnum.SUCCESS, false);

        parseRecordItemAndCommit(new RecordItem(signTransaction, signTransactionRecord));
    }

    private void insertScheduledTransaction(long signTimestamp, ScheduleID scheduleID,
                                            ResponseCodeEnum responseCodeEnum) throws InvalidProtocolBufferException {
        Transaction scheduledTransaction = scheduledTransaction();
        TransactionBody scheduledTransactionBody = getTransactionBody(scheduledTransaction);
        var scheduledTransactionRecord = createTransactionRecord(signTimestamp, scheduleID, scheduledTransactionBody,
                responseCodeEnum, true);

        parseRecordItemAndCommit(new RecordItem(scheduledTransaction, scheduledTransactionRecord));
    }

    private void assertScheduleInRepository(ScheduleID scheduleID, long createdTimestamp, AccountID payer,
                                            Long executedTimestamp) {
        assertThat(scheduleRepository.findById(createdTimestamp)).get()
                .returns(createdTimestamp, from(Schedule::getConsensusTimestamp))
                .returns(executedTimestamp, from(Schedule::getExecutedTimestamp))
                .returns(EntityId.of(scheduleID), from(Schedule::getScheduleId))
                .returns(EntityId.of(PAYER), from(Schedule::getCreatorAccountId))
                .returns(EntityId.of(payer), from(Schedule::getPayerAccountId))
                .returns(SCHEDULED_TRANSACTION_BODY.toByteArray(), from(Schedule::getTransactionBody));
    }

    private void assertScheduleSignatureInRepository(List<ScheduleSignature> expected) {
        Iterable<ScheduleSignature> scheduleSignatures = scheduleSignatureRepository.findAll();
        assertThat(scheduleSignatures).isNotNull();
        assertThat(scheduleSignatures).hasSameElementsAs(expected);
    }

    private void assertTransactionInRepository(long consensusTimestamp, boolean scheduled,
            ResponseCodeEnum responseCode) {
        assertThat(transactionRepository.findById(consensusTimestamp)).get()
                .returns(scheduled, from(com.hedera.mirror.importer.domain.Transaction::isScheduled))
                .returns(responseCode.getNumber(), from(com.hedera.mirror.importer.domain.Transaction::getResult));
    }

    private List<ScheduleSignature> toScheduleSignatureList(long timestamp, ScheduleID scheduleId,
                                                            SignatureMap signatureMap) {
        return signatureMap.getSigPairList()
                .stream()
                .map(pair -> {
                    ScheduleSignature scheduleSignature = new ScheduleSignature();
                    scheduleSignature.setId(new ScheduleSignature.Id(
                            timestamp,
                            pair.getPubKeyPrefix().toByteArray())
                    );
                    scheduleSignature.setScheduleId(EntityId.of(scheduleId));
                    scheduleSignature.setSignature(pair.getEd25519().toByteArray());
                    return scheduleSignature;
                })
                .collect(Collectors.toUnmodifiableList());
    }
}
