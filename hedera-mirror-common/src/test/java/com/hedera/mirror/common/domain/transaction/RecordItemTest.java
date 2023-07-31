/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.common.domain.transaction;

/*
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.util.Version;

@SuppressWarnings("deprecation")
class RecordItemTest {

    private static final Version DEFAULT_HAPI_VERSION = new Version(0, 22, 0);
    private static final Transaction DEFAULT_TRANSACTION = Transaction.newBuilder()
            .setSignedTransactionBytes(SignedTransaction.getDefaultInstance().getBodyBytes())
            .build();
    private static final byte[] DEFAULT_TRANSACTION_BYTES = DEFAULT_TRANSACTION.toByteArray();
    private static final TransactionRecord DEFAULT_RECORD = TransactionRecord.getDefaultInstance();
    private static final byte[] DEFAULT_RECORD_BYTES = DEFAULT_RECORD.toByteArray();

    private static final TransactionBody TRANSACTION_BODY = TransactionBody.newBuilder()
            .setTransactionFee(10L)
            .setMemo("memo")
            .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
            .build();

    private static final SignatureMap SIGNATURE_MAP = SignatureMap.newBuilder()
            .addSigPair(SignaturePair.newBuilder()
                    .setEd25519(ByteString.copyFromUtf8("ed25519"))
                    .setPubKeyPrefix(ByteString.copyFromUtf8("pubKeyPrefix"))
                    .build())
            .build();

    private static final SignedTransaction SIGNED_TRANSACTION = SignedTransaction.newBuilder()
            .setBodyBytes(TRANSACTION_BODY.toByteString())
            .setSigMap(SIGNATURE_MAP)
            .build();

    private static final TransactionRecord TRANSACTION_RECORD = TransactionRecord.newBuilder()
            .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
            .setMemo("memo")
            .build();

    @CsvSource({
        ", FEE_SCHEDULE_FILE_PART_UPLOADED, true",
        ", SUCCESS, true",
        ", SUCCESS_BUT_MISSING_EXPECTED_OPERATION, true",
        "FEE_SCHEDULE_FILE_PART_UPLOADED, FEE_SCHEDULE_FILE_PART_UPLOADED, true",
        "SUCCESS, SUCCESS, true",
        "SUCCESS_BUT_MISSING_EXPECTED_OPERATION, SUCCESS_BUT_MISSING_EXPECTED_OPERATION, true",
        "INVALID_TRANSACTION, FEE_SCHEDULE_FILE_PART_UPLOADED, false",
        "INVALID_TRANSACTION, SUCCESS, false",
        "INVALID_TRANSACTION, SUCCESS_BUT_MISSING_EXPECTED_OPERATION, false",
        "INVALID_TRANSACTION, INVALID_TRANSACTION, false",
        ", INVALID_TRANSACTION, false"
    })
    @ParameterizedTest
    void isSuccessful(ResponseCodeEnum parentStatus, ResponseCodeEnum childStatus, boolean expected) {
        RecordItem parent = null;
        if (parentStatus != null) {
            var parentRecord = TRANSACTION_RECORD.toBuilder();
            parentRecord.getReceiptBuilder().setStatus(parentStatus);
            parent = RecordItem.builder()
                    .hapiVersion(DEFAULT_HAPI_VERSION)
                    .transactionRecord(parentRecord.build())
                    .transaction(Transaction.newBuilder().build())
                    .build();
        }

        var childRecord = TRANSACTION_RECORD.toBuilder();
        childRecord.getReceiptBuilder().setStatus(childStatus);
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .parent(parent)
                .transactionRecord(childRecord.build())
                .transaction(Transaction.newBuilder().build())
                .build();
        assertThat(recordItem.isSuccessful()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAddEntityId(boolean accept) {
        var random = new SecureRandom();
        long id = random.nextLong(2000) + 2000L;
        var now = Instant.now();
        var payerAccountId = AccountID.newBuilder().setAccountNum(id++).build();
        long consensusTimestamp = now.getEpochSecond() * 1_000_000_000 + now.getNano();
        var validStart =
                Timestamp.newBuilder().setSeconds(now.getEpochSecond() - 1).setNanos(now.getNano());
        var transactionBody = TransactionBody.newBuilder()
                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                .setTransactionID(
                        TransactionID.newBuilder().setAccountID(payerAccountId).setTransactionValidStart(validStart))
                .build();
        var signedTransaction = SignedTransaction.newBuilder()
                .setBodyBytes(transactionBody.toByteString())
                .setSigMap(SIGNATURE_MAP)
                .build();
        var transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTransaction.toByteString())
                .build();
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()))
                .setReceipt(TransactionReceipt.newBuilder().setStatus(ResponseCodeEnum.SUCCESS))
                .build();
        var recordItem = RecordItem.builder()
                .entityTransactionPredicate(e -> accept)
                .transaction(transaction)
                .transactionRecord(transactionRecord)
                .build();
        var account = EntityId.of(id, EntityType.ACCOUNT);
        var expected = accept
                ? Map.of(
                        id,
                        EntityTransaction.builder()
                                .consensusTimestamp(consensusTimestamp)
                                .entityId(id)
                                .payerAccountId(EntityId.of(payerAccountId))
                                .result(ResponseCodeEnum.SUCCESS_VALUE)
                                .type(TransactionType.CRYPTOTRANSFER.getProtoId())
                                .build())
                : new HashMap<Long, EntityTransaction>();

        // when
        recordItem.addEntityId(account);

        // then
        assertThat(recordItem.getEntityTransactions()).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void testBadTransactionBytesThrowException() {
        testException(new byte[] {0x0, 0x1}, DEFAULT_RECORD_BYTES, RecordItem.BAD_TRANSACTION_BYTES_MESSAGE);
    }

    @Test
    void testBadRecordBytesThrowException() {
        testException(DEFAULT_TRANSACTION_BYTES, new byte[] {0x0, 0x1}, RecordItem.BAD_RECORD_BYTES_MESSAGE);
    }

    @Test
    void testTransactionBytesWithoutTransactionBodyThrowException() {
        testException(
                Transaction.newBuilder().build().toByteArray(),
                DEFAULT_RECORD_BYTES,
                RecordItem.BAD_TRANSACTION_BODY_BYTES_MESSAGE);
    }

    @Test
    void testWithBody() {
        Transaction transaction = Transaction.newBuilder()
                .setBody(TRANSACTION_BODY)
                .setSigMap(SIGNATURE_MAP)
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithBodyProto() {
        // An encoded protobuf Transaction with the body set in TransactionBody, as seen in an older proto version
        byte[] transactionFromProto = Base64.decodeBase64("CgoYCjIEbWVtb3IAGhkKFwoMcHViS2V5UHJlZml4GgdlZDI1NTE5");

        Transaction expectedTransaction = Transaction.newBuilder()
                .setBody(TRANSACTION_BODY)
                .setSigMap(SIGNATURE_MAP)
                .build();

        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecordBytes(TRANSACTION_RECORD.toByteArray())
                .transactionBytes(transactionFromProto)
                .build();
        assertRecordItem(expectedTransaction, recordItem);
    }

    @Test
    void testWithBodyBytes() {
        Transaction transaction = Transaction.newBuilder()
                .setBodyBytes(TRANSACTION_BODY.toByteString())
                .setSigMap(SIGNATURE_MAP)
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithSignedTransaction() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithParentItems() {
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithParentTimestampNoPrevious() {
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithPreviousButNoParentTimestamp() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(previousRecordItem)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithNonMatchingPreviousTimestamp() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(previousRecordItem)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithParent() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(transactionRecord)
                .previous(previousRecordItem)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent is picked up for a valid previous
        assertThat(recordItem)
                .returns(previousRecordItem, RecordItem::getParent)
                .satisfies(c -> assertThat(c.getParent()).isNotNull());
    }

    @Test
    void testWithParentFromSiblingReference() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem parentRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var siblingTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem siblingRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .parent(parentRecordItem)
                .transactionRecord(siblingTransactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(siblingRecordItem)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent is picked up for a valid previous
        assertThat(recordItem).returns(parentRecordItem, RecordItem::getParent).satisfies(c -> assertThat(c.getParent())
                .isNotNull());
    }

    @Test
    void testWithNonMatchingParentFromSiblingReference() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem parentRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(parentTransactionRecord)
                .transaction(transaction)
                .build();

        var siblingTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem siblingRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(siblingTransactionRecord)
                .parent(parentRecordItem)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setParentConsensusTimestamp(
                        Timestamp.newBuilder().setSeconds(7).setNanos(8).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(siblingRecordItem)
                .transactionRecord(transactionRecord)
                .transaction(DEFAULT_TRANSACTION)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    /**
     * This test writes a TransactionBody that contains an unknown field with a protobuf ID of 9999 to test that the
     * unknown transaction is still inserted into the database.
     */
    @Test
    void unknownTransactionType() throws Exception {
        int unknownType = 9999;
        byte[] transactionBodyBytes = Hex.decodeHex(
                "0a120a0c08eb88d6ee0510e8eff7ab01120218021202180318c280de1922020878321043727970746f2074657374206d656d6ffaf004050a03666f6f");
        Transaction transaction = Transaction.newBuilder()
                .setBodyBytes(ByteString.copyFrom(transactionBodyBytes))
                .build();

        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();

        assertThat(recordItem.getTransactionType()).isEqualTo(unknownType);
    }

    /**
     * This test writes a TransactionBody that contains an invalid transaction body without unknown fields or a valid
     * transaction body and verifies it is still inserted into the database.
     * <p>
     * See https://github.com/hashgraph/hedera-mirror-node/issues/4843
     */
    @Test
    void invalidTransactionType() {
        byte[] invalidBytes = new byte[] {
            10, 23, 10, 21, 10, 11, 8, -23, -105, -78, -101, 6, 16, -115, -95, -56, 47, 18, 4, 24, -108, -74, 85, 32, 1
        };
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(ByteString.copyFrom(invalidBytes))
                .build();

        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionRecord(TRANSACTION_RECORD)
                .transaction(transaction)
                .build();

        assertThat(recordItem.getTransactionType()).isEqualTo(TransactionBody.DataCase.DATA_NOT_SET.getNumber());
    }

    @SuppressWarnings("java:S5778")
    private void testException(byte[] transactionBytes, byte[] recordBytes, String expectedMessage) {
        assertThatThrownBy(() -> RecordItem.builder()
                        .hapiVersion(DEFAULT_HAPI_VERSION)
                        .transactionRecordBytes(recordBytes)
                        .transactionBytes(transactionBytes)
                        .build()
                        .getTransactionBody())
                .isInstanceOf(ProtobufException.class)
                .hasMessage(expectedMessage);
    }

    private void assertRecordItem(Transaction transaction, RecordItem recordItem) {
        assertThat(recordItem.getHapiVersion()).isEqualTo(DEFAULT_HAPI_VERSION);
        assertThat(recordItem.getTransaction()).isEqualTo(transaction);
        assertThat(recordItem.getTransactionRecord()).isEqualTo(TRANSACTION_RECORD);
        assertThat(recordItem.getTransactionBody()).isEqualTo(TRANSACTION_BODY);
        assertThat(recordItem.getTransactionBytes()).isEqualTo(transaction.toByteArray());
        assertThat(recordItem.getRecordBytes()).isEqualTo(TRANSACTION_RECORD.toByteArray());
        assertThat(recordItem.getSignatureMap()).isEqualTo(SIGNATURE_MAP);
    }
}
