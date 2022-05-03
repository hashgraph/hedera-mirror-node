package com.hedera.mirror.common.domain.transaction;

/*
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;
import org.springframework.data.util.Version;

import com.hedera.mirror.common.exception.ProtobufException;

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
            .addSigPair(
                    SignaturePair.newBuilder()
                            .setEd25519(ByteString.copyFromUtf8("ed25519"))
                            .setPubKeyPrefix(ByteString.copyFromUtf8("pubKeyPrefix"))
                            .build()
            ).build();

    private static final SignedTransaction SIGNED_TRANSACTION = SignedTransaction.newBuilder()
            .setBodyBytes(TRANSACTION_BODY.toByteString())
            .setSigMap(SIGNATURE_MAP)
            .build();

    private static final TransactionRecord TRANSACTION_RECORD = TransactionRecord.newBuilder()
            .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
            .setMemo("memo")
            .build();

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
        testException(Transaction.newBuilder().build().toByteArray(),
                DEFAULT_RECORD_BYTES, RecordItem.BAD_TRANSACTION_BODY_BYTES_MESSAGE);
    }

    @Test
    void testWithBody() {
        Transaction transaction = Transaction.newBuilder()
                .setBody(TRANSACTION_BODY)
                .setSigMap(SIGNATURE_MAP)
                .build();
        RecordItem recordItem = new RecordItem(DEFAULT_HAPI_VERSION, transaction, TRANSACTION_RECORD);
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithBodyProto() {
        //An encoded protobuf Transaction with the body set in TransactionBody, as seen in an older proto version
        byte[] transactionFromProto = Base64
                .decodeBase64("CgoYCjIEbWVtb3IAGhkKFwoMcHViS2V5UHJlZml4GgdlZDI1NTE5");

        Transaction expectedTransaction = Transaction.newBuilder()
                .setBody(TRANSACTION_BODY)
                .setSigMap(SIGNATURE_MAP)
                .build();

        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transactionBytes(transactionFromProto)
                .recordBytes(TRANSACTION_RECORD.toByteArray())
                .build();
        assertRecordItem(expectedTransaction, recordItem);
    }

    @Test
    void testWithBodyBytes() {
        Transaction transaction = Transaction.newBuilder()
                .setBodyBytes(TRANSACTION_BODY.toByteString())
                .setSigMap(SIGNATURE_MAP)
                .build();
        RecordItem recordItem = new RecordItem(DEFAULT_HAPI_VERSION, transaction, TRANSACTION_RECORD);
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithSignedTransaction() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();
        RecordItem recordItem = new RecordItem(DEFAULT_HAPI_VERSION, transaction, TRANSACTION_RECORD);
        assertRecordItem(transaction, recordItem);
    }

    @Test
    void testWithParentItems() {
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transaction(DEFAULT_TRANSACTION)
                .record(transactionRecord)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    @Test
    void testWithParentTimestampNoPrevious() {
        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setParentConsensusTimestamp(Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transaction(DEFAULT_TRANSACTION)
                .record(transactionRecord)
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
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = new RecordItem(DEFAULT_HAPI_VERSION, transaction,
                parentTransactionRecord);

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(2).setNanos(4).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .previous(previousRecordItem)
                .transaction(DEFAULT_TRANSACTION)
                .record(transactionRecord)
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
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = new RecordItem(DEFAULT_HAPI_VERSION, transaction,
                parentTransactionRecord);

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transaction(DEFAULT_TRANSACTION)
                .record(transactionRecord)
                .previous(previousRecordItem)
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
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem previousRecordItem = new RecordItem(DEFAULT_HAPI_VERSION, transaction,
                parentTransactionRecord);

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transaction(DEFAULT_TRANSACTION)
                .record(transactionRecord)
                .previous(previousRecordItem)
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
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem parentRecordItem = new RecordItem(DEFAULT_HAPI_VERSION, transaction,
                parentTransactionRecord);

        var siblingTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem siblingRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transaction(DEFAULT_TRANSACTION)
                .record(siblingTransactionRecord)
                .parent(parentRecordItem)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transaction(DEFAULT_TRANSACTION)
                .record(transactionRecord)
                .previous(siblingRecordItem)
                .build();

        // verify parent is picked up for a valid previous
        assertThat(recordItem)
                .returns(parentRecordItem, RecordItem::getParent)
                .satisfies(c -> assertThat(c.getParent()).isNotNull());
    }

    @Test
    void testWithNonMatchingParentFromSiblingReference() {
        Transaction transaction = Transaction.newBuilder()
                .setSignedTransactionBytes(SIGNED_TRANSACTION.toByteString())
                .build();

        var parentTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(1).setNanos(2).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("parent")
                .build();

        RecordItem parentRecordItem = new RecordItem(DEFAULT_HAPI_VERSION, transaction,
                parentTransactionRecord);

        var siblingTransactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(3).setNanos(4).build())
                .setParentConsensusTimestamp(parentTransactionRecord.getConsensusTimestamp())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem siblingRecordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transaction(DEFAULT_TRANSACTION)
                .record(siblingTransactionRecord)
                .parent(parentRecordItem)
                .build();

        var transactionRecord = TransactionRecord.newBuilder()
                .setConsensusTimestamp(Timestamp.newBuilder().setSeconds(5).setNanos(6).build())
                .setParentConsensusTimestamp(Timestamp.newBuilder().setSeconds(7).setNanos(8).build())
                .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
                .setMemo("child")
                .build();
        RecordItem recordItem = RecordItem.builder()
                .hapiVersion(DEFAULT_HAPI_VERSION)
                .transaction(DEFAULT_TRANSACTION)
                .record(transactionRecord)
                .previous(siblingRecordItem)
                .build();

        // verify parent
        assertThat(recordItem.getParent()).isNull();
    }

    /**
     * This test writes a TransactionBody that contains a unknown field with a protobuf ID of 9999 to test that the
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
        RecordItem recordItem = new RecordItem(transaction, TRANSACTION_RECORD);

        assertThat(recordItem.getTransactionType()).isEqualTo(unknownType);
    }

    private void testException(byte[] transactionBytes, byte[] recordBytes, String expectedMessage) {
        assertThatThrownBy(() -> new RecordItem(DEFAULT_HAPI_VERSION, transactionBytes, recordBytes, null))
                .isInstanceOf(ProtobufException.class)
                .hasMessage(expectedMessage);
    }

    private void assertRecordItem(Transaction transaction, RecordItem recordItem) {
        assertThat(recordItem.getHapiVersion()).isEqualTo(DEFAULT_HAPI_VERSION);
        assertThat(recordItem.getTransaction()).isEqualTo(transaction);
        assertThat(recordItem.getRecord()).isEqualTo(TRANSACTION_RECORD);
        assertThat(recordItem.getTransactionBody()).isEqualTo(TRANSACTION_BODY);
        assertThat(recordItem.getTransactionBytes()).isEqualTo(transaction.toByteArray());
        assertThat(recordItem.getRecordBytes()).isEqualTo(TRANSACTION_RECORD.toByteArray());
        assertThat(recordItem.getSignatureMap()).isEqualTo(SIGNATURE_MAP);
    }
}
