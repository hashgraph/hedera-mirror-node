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

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.importer.parser.domain.RecordItem;

public class EntityRecordItemListenerFreezeTest extends AbstractEntityRecordItemListenerTest {

    @BeforeEach
    void before() {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void freeze() throws Exception {
        Transaction transaction = freezeTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCount(),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void freezeInvalidTransaction() throws Exception {
        Transaction transaction = freezeTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(new RecordItem(transaction, record));

        assertAll(
                () -> assertRowCount(),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    private void assertRowCount() {
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(3, entityRepository.count()),
                () -> assertEquals(0, contractResultRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count()),
                () -> assertEquals(0, liveHashRepository.count()),
                () -> assertEquals(0, fileDataRepository.count())
        );
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody) {
        return transactionRecord(transactionBody, ResponseCodeEnum.SUCCESS);
    }

    private TransactionRecord transactionRecord(TransactionBody transactionBody, ResponseCodeEnum result) {
        return buildTransactionRecord(recordBuilder -> {
        }, transactionBody, result.getNumber());
    }

    private Transaction freezeTransaction() {
        return buildTransaction(builder -> builder.getFreezeBuilder()
                .setEndHour(1)
                .setEndMin(2)
                .setStartHour(3)
                .setStartMin(4)
                .setUpdateFile(FileID.newBuilder().setFileNum(5).build()));
    }
}
