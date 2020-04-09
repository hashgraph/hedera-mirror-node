package com.hedera.mirror.importer.parser.domain;

/*
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

import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import org.apache.commons.codec.binary.Hex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.exception.ParserException;

class RecordItemTest {

    private static final Transaction DEFAULT_TRANSACTION = Transaction.newBuilder()
            .setBodyBytes(TransactionBody.getDefaultInstance().toByteString())
            .build();
    private static final byte[] DEFAULT_TRANSACTION_BYTES = DEFAULT_TRANSACTION.toByteArray();
    private static final TransactionRecord DEFAULT_RECORD = TransactionRecord.getDefaultInstance();
    private static final byte[] DEFAULT_RECORD_BYTES = DEFAULT_RECORD.toByteArray();

    // 'body' and 'bodyBytes' feilds left empty
    private static final Transaction TRANSACTION = Transaction.newBuilder()
            .setSigMap(SignatureMap.newBuilder()
                    .addSigPair(SignaturePair.newBuilder()
                            .setEd25519(ByteString.copyFromUtf8("ed25519"))
                            .setPubKeyPrefix(ByteString.copyFromUtf8("pubKeyPrefix"))
                            .build())
                    .build())
            .build();

    private static final TransactionBody TRANSACTION_BODY = TransactionBody.newBuilder()
            .setTransactionFee(10L)
            .setMemo("memo")
            .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
            .build();

    private static final TransactionRecord TRANSACTION_RECORD = TransactionRecord.newBuilder()
            .setReceipt(TransactionReceipt.newBuilder().setStatusValue(22).build())
            .setMemo("memo")
            .build();

    @Test
    public void testBadTransactionBytesThrowException() {
        testException(new byte[] {0x0, 0x1}, DEFAULT_RECORD_BYTES, RecordItem.BAD_TRANSACTION_BYTES_MESSAGE);
    }

    @Test
    public void testBadRecordBytesThrowException() {
        testException(DEFAULT_TRANSACTION_BYTES, new byte[] {0x0, 0x1}, RecordItem.BAD_RECORD_BYTES_MESSAGE);
    }

    @Test
    public void testTransactionBytesWithoutTransactionBodyThrowException() {
        testException(Transaction.newBuilder().build().toByteArray(),
                DEFAULT_RECORD_BYTES, RecordItem.BAD_TRANSACTION_BODY_BYTES_MESSAGE);
    }

    @Test
    public void testWithBody() {
        Transaction transaction = TRANSACTION.toBuilder().setBody(TRANSACTION_BODY).build();
        RecordItem recordItem = new RecordItem(transaction.toByteArray(), TRANSACTION_RECORD.toByteArray());
        assertRecordItem(transaction, recordItem);
    }

    @Test
    public void testWithBodyBytes() {
        Transaction transaction = TRANSACTION.toBuilder().setBodyBytes(TRANSACTION_BODY.toByteString()).build();
        RecordItem recordItem = new RecordItem(transaction.toByteArray(), TRANSACTION_RECORD.toByteArray());
        assertRecordItem(transaction, recordItem);
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
        Transaction transaction = TRANSACTION.toBuilder()
                .setBodyBytes(ByteString.copyFrom(transactionBodyBytes))
                .build();
        RecordItem recordItem = new RecordItem(transaction, TRANSACTION_RECORD);

        assertThat(recordItem.getTransactionType()).isEqualTo(unknownType);
    }

    private void testException(byte[] transactionBytes, byte[] recordBytes, String expectedMessage) {
        assertThatThrownBy(() -> new RecordItem(transactionBytes, recordBytes))
                .isInstanceOf(ParserException.class)
                .hasMessage(expectedMessage);
    }

    private void assertRecordItem(Transaction transaction, RecordItem recordItem) {
        assertThat(recordItem.getTransaction()).isEqualTo(transaction);
        assertThat(recordItem.getRecord()).isEqualTo(TRANSACTION_RECORD);
        assertThat(recordItem.getTransactionBody()).isEqualTo(TRANSACTION_BODY);
        assertThat(recordItem.getTransactionBytes()).isEqualTo(transaction.toByteArray());
        assertThat(recordItem.getRecordBytes()).isEqualTo(TRANSACTION_RECORD.toByteArray());
    }
}
