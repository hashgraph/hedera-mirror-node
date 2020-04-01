package com.hedera.mirror.importer.parser.domain;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.AllArgsConstructor;
import lombok.Value;

import com.hedera.mirror.importer.exception.ParserException;

@Value
@AllArgsConstructor
public class RecordItem implements StreamItem {
    static final String BAD_TRANSACTION_BYTES_MESSAGE = "Failed to parse transaction bytes";
    static final String BAD_RECORD_BYTES_MESSAGE = "Failed to parse record bytes";
    static final String BAD_TRANSACTION_BODY_BYTES_MESSAGE = "Error parsing transactionBody from bodyBytes";

    private final Transaction transaction;
    private final TransactionBody transactionBody;
    private final TransactionRecord record;
    private final byte[] transactionBytes;
    private final byte[] recordBytes;

    /**
     * Constructs RecordItem from serialized transactionBytes and recordBytes.
     *
     * @throws ParserException if
     *                         <ul>
     *                          <li>transactionBytes or recordBytes fail to parse</li>
     *                          <li>parsed Transaction does not have valid TransactionBody i.e. 'body' is not set
     *                          and 'bodyBytes' is empty</li>
     *                          <li>parsed Transaction has non-empty 'bodyBytes' and it fails to parse</li>
     *                         </ul>
     */
    public RecordItem(byte[] transactionBytes, byte[] recordBytes) {
        try {
            transaction = Transaction.parseFrom(transactionBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new ParserException(BAD_TRANSACTION_BYTES_MESSAGE, e);
        }
        try {
            record = TransactionRecord.parseFrom(recordBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new ParserException(BAD_RECORD_BYTES_MESSAGE, e);
        }
        transactionBody = parseTransactionBody(transaction);
        this.transactionBytes = transactionBytes;
        this.recordBytes = recordBytes;
    }

    // Used only in tests
    public RecordItem(Transaction transaction, TransactionRecord record) {
        this.transaction = transaction;
        transactionBody = parseTransactionBody(transaction);
        this.record = record;
        transactionBytes = null;
        recordBytes = null;
    }

    private static TransactionBody parseTransactionBody(Transaction transaction) {
        if (transaction.hasBody()) {
            return transaction.getBody();
        } else {
            // Not possible to check existence of bodyBytes field since there is no 'hasBodyBytes()'.
            // If unset, getBodyBytes() returns empty ByteString which always parses successfully to "empty"
            //TransactionBody. However, every transaction should have a valid (non "empty") TransactionBody.
            if (transaction.getBodyBytes() == ByteString.EMPTY) {
                throw new ParserException(BAD_TRANSACTION_BODY_BYTES_MESSAGE);
            }
            try {
                return TransactionBody.parseFrom(transaction.getBodyBytes());
            } catch (InvalidProtocolBufferException e) {
                throw new ParserException(BAD_TRANSACTION_BODY_BYTES_MESSAGE, e);
            }
        }
    }
}
