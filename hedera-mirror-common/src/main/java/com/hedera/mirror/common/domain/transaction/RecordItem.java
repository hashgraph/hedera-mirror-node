package com.hedera.mirror.common.domain.transaction;

/*-
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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Version;

import com.hedera.mirror.common.domain.StreamItem;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hedera.mirror.common.util.DomainUtils;

@Builder(buildMethodName = "buildInternal")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Log4j2
@Value
public class RecordItem implements StreamItem {
    static final String BAD_TRANSACTION_BYTES_MESSAGE = "Failed to parse transaction bytes";
    static final String BAD_RECORD_BYTES_MESSAGE = "Failed to parse record bytes";
    static final String BAD_TRANSACTION_BODY_BYTES_MESSAGE = "Error parsing transactionBody from transaction";

    private final Version hapiVersion;
    private final Transaction transaction;
    private final TransactionBodyAndSignatureMap transactionBodyAndSignatureMap;
    private final TransactionRecord record;
    // This field is not TransactionType since in case of unknown type, we want exact numerical value rather than
    // -1 in enum.
    private final int transactionType;
    private final byte[] transactionBytes;
    private final byte[] recordBytes;

    @Getter(lazy = true)
    private long consensusTimestamp = DomainUtils.timestampInNanosMax(record.getConsensusTimestamp());

    // transactions in stream always have a valid payerAccountId
    @Getter(lazy = true)
    private EntityId payerAccountId = EntityId.of(getTransactionBody().getTransactionID().getAccountID());

    private final Integer transactionIndex;

    private final RecordItem parent;

    private RecordItem previous;

    /**
     * Constructs RecordItem from serialized transactionBytes and recordBytes.
     */
    public RecordItem(Version hapiVersion, byte[] transactionBytes, byte[] recordBytes, Integer transactionIndex) {
        try {
            transaction = Transaction.parseFrom(transactionBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtobufException(BAD_TRANSACTION_BYTES_MESSAGE, e);
        }
        try {
            record = TransactionRecord.parseFrom(recordBytes);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtobufException(BAD_RECORD_BYTES_MESSAGE, e);
        }
        transactionBodyAndSignatureMap = parseTransactionBodyAndSignatureMap(transaction);
        transactionType = getTransactionType(transactionBodyAndSignatureMap.getTransactionBody());

        this.hapiVersion = hapiVersion;
        this.transactionBytes = transactionBytes;
        this.recordBytes = recordBytes;
        this.transactionIndex = transactionIndex;
        parent = null;
        previous = null;
    }

    // Used only in tests
    // There are many brittle RecordItemParser*Tests which rely on bytes being null. Those tests need to be fixed,
    // then this function can be removed.
    public RecordItem(Version hapiVersion, Transaction transaction, TransactionRecord record) {
        Objects.requireNonNull(transaction, "transaction is required");
        Objects.requireNonNull(record, "record is required");

        this.hapiVersion = hapiVersion;
        this.transaction = transaction;
        transactionBodyAndSignatureMap = parseTransactionBodyAndSignatureMap(transaction);
        transactionType = getTransactionType(transactionBodyAndSignatureMap.getTransactionBody());
        this.record = record;
        transactionBytes = transaction.toByteArray();
        recordBytes = record.toByteArray();
        transactionIndex = null;
        parent = null;
        previous = null;
    }

    // Used only in tests, default hapiVersion to RecordFile.HAPI_VERSION_NOT_SET
    public RecordItem(Transaction transaction, TransactionRecord record) {
        this(RecordFile.HAPI_VERSION_NOT_SET, transaction, record);
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
                // TransactionBody. However, every transaction should have a valid (non "empty") TransactionBody.
                return new TransactionBodyAndSignatureMap(TransactionBody
                        .parseFrom(transaction.getBodyBytes()), transaction.getSigMap());
            } else if (transaction.hasBody()) {
                return new TransactionBodyAndSignatureMap(transaction.getBody(), transaction.getSigMap());
            }
            throw new ProtobufException(BAD_TRANSACTION_BODY_BYTES_MESSAGE);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtobufException(BAD_TRANSACTION_BODY_BYTES_MESSAGE, e);
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

    public TransactionBody getTransactionBody() {
        return transactionBodyAndSignatureMap.getTransactionBody();
    }

    public SignatureMap getSignatureMap() {
        return transactionBodyAndSignatureMap.getSignatureMap();
    }

    public boolean isSuccessful() {
        return record.getReceipt().getStatus() == ResponseCodeEnum.SUCCESS;
    }

    public boolean isChild() {
        return record.hasParentConsensusTimestamp();
    }

    @Value
    private static class TransactionBodyAndSignatureMap {
        private TransactionBody transactionBody;
        private SignatureMap signatureMap;
    }

    // Necessary since Lombok doesn't use our setters for builders
    public static class RecordItemBuilder<C, B extends RecordItem.RecordItemBuilder> {

        public RecordItem build() {
            // set parent, parent-child items are assured to exist in sequential order of [Parent, Child1,..., ChildN]
            if (record.hasParentConsensusTimestamp() && previous != null) {
                if (record.getParentConsensusTimestamp()
                        .equals(previous.record.getConsensusTimestamp())) {
                    // check immediately preceding item
                    parent = previous;
                } else if (previous.parent != null && record.getParentConsensusTimestamp()
                        .equals(previous.parent.record.getConsensusTimestamp())) {
                    // check older siblings parent, if child count is > 1 this prevents having to search to parent
                    parent = previous.parent;
                }
            }

            return buildInternal();
        }

        public B transactionBytes(byte[] transactionBytes) {
            try {
                transaction = Transaction.parseFrom(transactionBytes);
            } catch (InvalidProtocolBufferException e) {
                throw new ProtobufException(BAD_TRANSACTION_BYTES_MESSAGE, e);
            }
            transactionBodyAndSignatureMap = parseTransactionBodyAndSignatureMap(transaction);
            transactionType = getTransactionType(transactionBodyAndSignatureMap.getTransactionBody());
            this.transactionBytes = transactionBytes;
            return (B) this;
        }

        public B recordBytes(byte[] recordBytes) {
            try {
                record = TransactionRecord.parseFrom(recordBytes);
            } catch (InvalidProtocolBufferException e) {
                throw new ProtobufException(BAD_RECORD_BYTES_MESSAGE, e);
            }

            this.recordBytes = recordBytes;
            return (B) this;
        }
    }
}
