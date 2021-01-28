package com.hedera.mirror.importer.parser.domain;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Set;
import lombok.Getter;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.exception.ParserException;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Value
public class RecordItem implements StreamItem {
    static final String BAD_TRANSACTION_BYTES_MESSAGE = "Failed to parse transaction bytes";
    static final String BAD_RECORD_BYTES_MESSAGE = "Failed to parse record bytes";
    static final String BAD_TRANSACTION_BODY_BYTES_MESSAGE = "Error parsing transactionBody from transaction";
    static final int TRANSACTION_BODY_PROTOBUF_TAG = 1;

    private final Transaction transaction;
    private final TransactionBodyAndSignatureMap transactionBodyAndSignatureMap;
    private final TransactionRecord record;
    // This field is not TransactionTypeEnum since in case of unknown type, we want exact numerical value rather than
    // -1 in enum.
    private final int transactionType;
    private final byte[] transactionBytes;
    private final byte[] recordBytes;

    @Getter(lazy = true)
    private long consensusTimestamp = Utility.timestampInNanosMax(record.getConsensusTimestamp());

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
        transactionBodyAndSignatureMap = parseTransactionBodyAndSignatureMap(transaction);
        transactionType = getTransactionType(transactionBodyAndSignatureMap.getTransactionBody());
        this.transactionBytes = transactionBytes;
        this.recordBytes = recordBytes;
    }

    // Used only in tests
    // There are many brittle RecordItemParser*Tests which rely on bytes being null. Those tests need to be fixed,
    // then this function can be removed.
    public RecordItem(Transaction transaction, TransactionRecord record) {
        this.transaction = transaction;
        transactionBodyAndSignatureMap = parseTransactionBodyAndSignatureMap(transaction);
        transactionType = getTransactionType(transactionBodyAndSignatureMap.getTransactionBody());
        this.record = record;
        transactionBytes = null;
        recordBytes = null;
    }

    public TransactionBody getTransactionBody() {
        return transactionBodyAndSignatureMap.getTransactionBody();
    }

    public SignatureMap getSignatureMap() {
        return transactionBodyAndSignatureMap.getSignatureMap();
    }

    private static TransactionBodyAndSignatureMap parseTransactionBodyAndSignatureMap(Transaction transaction) {
        try {
            if (!transaction.getSignedTransactionBytes().equals(ByteString.EMPTY)) {
                SignedTransaction signedTransaction = SignedTransaction
                        .parseFrom(transaction.getSignedTransactionBytes());
                return new TransactionBodyAndSignatureMap(TransactionBody
                        .parseFrom(signedTransaction.getBodyBytes()), signedTransaction.getSigMap());
            } else if (!transaction.getBodyBytes().equals(ByteString.EMPTY)) {
                // Not possible to check existence of bodyBytes field since there is no 'hasBodyBytes()'.
                // If unset, getBodyBytes() returns empty ByteString which always parses successfully to "empty"
                //TransactionBody. However, every transaction should have a valid (non "empty") TransactionBody.
                return new TransactionBodyAndSignatureMap(TransactionBody
                        .parseFrom(transaction.getBodyBytes()), transaction.getSigMap());
            } else if (transaction.getUnknownFields().hasField(TRANSACTION_BODY_PROTOBUF_TAG)) {
                TransactionBody transactionBody = TransactionBody
                        .parseFrom(transaction.getUnknownFields().getField(1).getLengthDelimitedList()
                                .get(0));
                return new TransactionBodyAndSignatureMap(transactionBody, transaction.getSigMap());
            }
            throw new ParserException(BAD_TRANSACTION_BODY_BYTES_MESSAGE);
        } catch (
                InvalidProtocolBufferException e) {
            throw new ParserException(BAD_TRANSACTION_BODY_BYTES_MESSAGE, e);
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
                throw new IllegalStateException("Unable to guess correct transaction type since there's not " +
                        "exactly one: " + unknownFields);
            }

            int transactionType = unknownFields.iterator().next();
            log.warn("Encountered unknown transaction type: {}", transactionType);
            return transactionType;
        }
        return dataCase.getNumber();
    }

    @Value
    private static class TransactionBodyAndSignatureMap {
        private TransactionBody transactionBody;
        private SignatureMap signatureMap;
    }
}
