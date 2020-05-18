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
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Value
@AllArgsConstructor
public class RecordItem implements StreamItem {
    static final String BAD_TRANSACTION_BYTES_MESSAGE = "Failed to parse transaction bytes";
    static final String BAD_RECORD_BYTES_MESSAGE = "Failed to parse record bytes";
    static final String BAD_TRANSACTION_BODY_BYTES_MESSAGE = "Error parsing transactionBody from bodyBytes";

    private final Transaction transaction;
    private final TransactionBody transactionBody;
    private final TransactionRecord record;
    // This field is not TransactionTypeEnum since in case of unknown type, we want exact numerical value rather than
    // -1 in enum.
    private final int transactionType;
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
        transactionType = getTransactionType(transactionBody);
        this.transactionBytes = transactionBytes;
        this.recordBytes = recordBytes;
    }

    // Used only in tests
    // There are many brittle RecordItemParser*Tests which rely on bytes being null. Those tests need to be fixed,
    // then this function can be removed.
    public RecordItem(Transaction transaction, TransactionRecord record) {
        this.transaction = transaction;
        transactionBody = parseTransactionBody(transaction);
        transactionType = getTransactionType(transactionBody);
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

    /**
     * Because body.getDataCase() can return null for unknown transaction types, we instead get oneof generically
     *
     * @return The protobuf ID that represents the transaction type
     */
    private static int getTransactionType(TransactionBody body) {
        TransactionBody.DataCase dataCase = body.getDataCase();

        if (dataCase == null || dataCase == TransactionBody.DataCase.DATA_NOT_SET) {
            Set<Integer> unknownFields = body.getUnknownFields().asMap().keySet();

            if (unknownFields.size() != 1) {
                throw new IllegalStateException("Unable to guess correct transaction type since there's not exactly " +
                        "one: " + unknownFields);
            }

            int transactionType = unknownFields.iterator().next();
            log.warn("Encountered unknown transaction type: {}", transactionType);
            return transactionType;
        }
        return dataCase.getNumber();
    }

    public Long getConsensusTimestamp() {
        return Utility.timestampInNanosMax(record.getConsensusTimestamp());
    }
}
