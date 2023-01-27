package com.hedera.mirror.importer.parser.record.entity;

/*-
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

import static org.junit.jupiter.api.Assertions.*;

import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hedera.mirror.common.domain.transaction.RecordItem;

class EntityRecordItemListenerFreezeTest extends AbstractEntityRecordItemListenerTest {

    @BeforeEach
    void before() {
        entityProperties.getPersist().setFiles(true);
        entityProperties.getPersist().setSystemFiles(true);
        entityProperties.getPersist().setCryptoTransferAmounts(true);
    }

    @Test
    void freeze() {
        Transaction transaction = freezeTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        assertAll(
                () -> assertRowCount(),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    @Test
    void freezeInvalidTransaction() {
        Transaction transaction = freezeTransaction();
        TransactionBody transactionBody = getTransactionBody(transaction);
        TransactionRecord record = transactionRecord(transactionBody, ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);

        parseRecordItemAndCommit(RecordItem.builder().transactionRecord(record).transaction(transaction).build());

        assertAll(
                () -> assertRowCount(),
                () -> assertTransactionAndRecord(transactionBody, record)
        );
    }

    private void assertRowCount() {
        assertAll(
                () -> assertEquals(1, transactionRepository.count()),
                () -> assertEquals(0, entityRepository.count()),
                () -> assertEquals(3, cryptoTransferRepository.count())
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
