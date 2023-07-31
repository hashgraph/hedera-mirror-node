/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

import static lombok.AccessLevel.PRIVATE;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.mirror.common.domain.StreamItem;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityTransaction;
import com.hedera.mirror.common.exception.ProtobufException;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.springframework.data.util.Version;

@Builder(buildMethodName = "buildInternal")
@AllArgsConstructor(access = PRIVATE)
@Log4j2
@Value
public class RecordItem implements StreamItem {

    static final String BAD_TRANSACTION_BYTES_MESSAGE = "Failed to parse transaction bytes";
    static final String BAD_RECORD_BYTES_MESSAGE = "Failed to parse record bytes";
    static final String BAD_TRANSACTION_BODY_BYTES_MESSAGE = "Error parsing transactionBody from transaction";

    // Final fields
    @Builder.Default
    private final Version hapiVersion = RecordFile.HAPI_VERSION_NOT_SET;

    private final RecordItem parent;
    private final RecordItem previous;
    private final TransactionRecord transactionRecord;
    private final byte[] recordBytes;
    private final Transaction transaction;
    private final byte[] transactionBytes;
    private final int transactionIndex;

    // Lazily calculated fields
    @Getter(lazy = true)
    private final long consensusTimestamp = DomainUtils.timestampInNanosMax(transactionRecord.getConsensusTimestamp());

    @Getter(lazy = true, value = PRIVATE)
    private final EntityTransaction.EntityTransactionBuilder entityTransactionBuilder = entityTransactionBuilder();

    @NonFinal
    @Setter
    private Predicate<EntityId> entityTransactionPredicate;

    @Builder.Default
    private final Map<Long, EntityTransaction> entityTransactions = new HashMap<>();

    @Getter(lazy = true)
    private final TransactionBodyAndSignatureMap transactionBodyAndSignatureMap = parseTransaction(transaction);

    @Getter(lazy = true)
    private final EntityId payerAccountId =
            EntityId.of(getTransactionBody().getTransactionID().getAccountID());

    @Getter(lazy = true)
    private boolean successful = checkSuccess();

    @Getter(lazy = true)
    private final int transactionType = getTransactionType(getTransactionBody());

    @Getter(lazy = true)
    private final int transactionStatus = getTransactionRecord().getReceipt().getStatusValue();

    // Mutable fields
    @Getter(PRIVATE)
    private final AtomicInteger logIndex = new AtomicInteger(0);

    @NonFinal
    @Setter
    private EthereumTransaction ethereumTransaction;

    @Builder.Default
    @NonFinal
    @Setter
    private List<TransactionSidecarRecord> sidecarRecords = Collections.emptyList();

    /**
     * Parses the transaction into separate TransactionBody and SignatureMap objects. Necessary since the Transaction
     * payload has changed incompatibly multiple times over its lifetime.
     * <p>
     * Not possible to check the existence of bodyBytes or signedTransactionBytes fields since there are no
     * 'hasBodyBytes()' or 'hasSignedTransactionBytes()` methods. If unset, they return empty ByteString which always
     * parses successfully to an empty TransactionBody. However, every transaction should have a valid (non-empty)
     * TransactionBody.
     *
     * @param transaction
     * @return the parsed transaction body and signature map
     */
    @SuppressWarnings("deprecation")
    private static TransactionBodyAndSignatureMap parseTransaction(Transaction transaction) {
        try {
            if (!transaction.getSignedTransactionBytes().equals(ByteString.EMPTY)) {
                var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
                var transactionBody = TransactionBody.parseFrom(signedTransaction.getBodyBytes());
                return new TransactionBodyAndSignatureMap(transactionBody, signedTransaction.getSigMap());
            } else if (!transaction.getBodyBytes().equals(ByteString.EMPTY)) {
                var transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
                return new TransactionBodyAndSignatureMap(transactionBody, transaction.getSigMap());
            } else if (transaction.hasBody()) {
                return new TransactionBodyAndSignatureMap(transaction.getBody(), transaction.getSigMap());
            }

            throw new ProtobufException(BAD_TRANSACTION_BODY_BYTES_MESSAGE);
        } catch (InvalidProtocolBufferException e) {
            throw new ProtobufException(BAD_TRANSACTION_BODY_BYTES_MESSAGE, e);
        }
    }

    public void addEntityId(EntityId entityId) {
        if (!entityTransactionPredicate.test(entityId)) {
            return;
        }

        entityTransactions.computeIfAbsent(
                entityId.getId(),
                id -> getEntityTransactionBuilder().entityId(id).build());
    }

    public int getAndIncrementLogIndex() {
        return logIndex.getAndIncrement();
    }

    public SignatureMap getSignatureMap() {
        return getTransactionBodyAndSignatureMap().signatureMap();
    }

    public TransactionBody getTransactionBody() {
        return getTransactionBodyAndSignatureMap().transactionBody();
    }

    public boolean isChild() {
        return transactionRecord.hasParentConsensusTimestamp();
    }

    private boolean checkSuccess() {
        // A child is only successful if its parent is as well. Consensus nodes have had issues in the past where that
        // invariant did not hold true and children contract calls were not reverted on failure.
        if (parent != null && !parent.isSuccessful()) {
            return false;
        }

        var status = transactionRecord.getReceipt().getStatus();
        return status == ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED
                || status == ResponseCodeEnum.SUCCESS
                || status == ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
    }

    private EntityTransaction.EntityTransactionBuilder entityTransactionBuilder() {
        return EntityTransaction.builder()
                .consensusTimestamp(getConsensusTimestamp())
                .payerAccountId(getPayerAccountId())
                .result(getTransactionStatus())
                .type(getTransactionType());
    }

    /**
     * Because body.getDataCase() can return null for unknown transaction types, we instead get oneof generically
     *
     * @return The protobuf ID that represents the transaction type
     */
    private int getTransactionType(TransactionBody body) {
        TransactionBody.DataCase dataCase = body.getDataCase();

        if (dataCase == null || dataCase == TransactionBody.DataCase.DATA_NOT_SET) {
            Set<Integer> unknownFields = body.getUnknownFields().asMap().keySet();

            if (unknownFields.size() != 1) {
                log.error(
                        "Unable to guess correct transaction type since there's not exactly one unknown field {}: {}",
                        unknownFields,
                        Hex.encodeHexString(body.toByteArray()));
                return TransactionBody.DataCase.DATA_NOT_SET.getNumber();
            }

            int genericTransactionType = unknownFields.iterator().next();
            log.warn("Encountered unknown transaction type: {}", genericTransactionType);
            return genericTransactionType;
        }

        return dataCase.getNumber();
    }

    /**
     * Check whether ethereum transaction exist in the record item and returns it hash, if not return 32-byte representation of the transaction hash
     *
     * @return 32-byte transaction hash of this record item
     */
    public byte[] getTransactionHash() {
        return Optional.ofNullable(getEthereumTransaction())
                .map(EthereumTransaction::getHash)
                .orElseGet(() -> Arrays.copyOfRange(
                        DomainUtils.toBytes(getTransactionRecord().getTransactionHash()), 0, 32));
    }

    private record TransactionBodyAndSignatureMap(TransactionBody transactionBody, SignatureMap signatureMap) {}

    public static class RecordItemBuilder {

        public RecordItem build() {
            // set parent, parent-child items are assured to exist in sequential order of [Parent, Child1,..., ChildN]
            if (transactionRecord.hasParentConsensusTimestamp() && previous != null) {
                var parentTimestamp = transactionRecord.getParentConsensusTimestamp();
                if (parentTimestamp.equals(
                        previous.transactionRecord.getConsensusTimestamp())) { // check immediately preceding
                    parent = previous;
                } else if (previous.parent != null
                        && parentTimestamp.equals(previous.parent.transactionRecord.getConsensusTimestamp())) {
                    // check older siblings parent, if child count is > 1 this prevents having to search to parent
                    parent = previous.parent;
                }
            }

            return buildInternal();
        }

        public RecordItemBuilder transactionRecord(TransactionRecord transactionRecord) {
            this.transactionRecord = transactionRecord;
            this.recordBytes = transactionRecord.toByteArray();
            return this;
        }

        public RecordItemBuilder transactionRecordBytes(byte[] recordBytes) {
            try {
                this.recordBytes = recordBytes;
                this.transactionRecord = TransactionRecord.parseFrom(recordBytes);
            } catch (InvalidProtocolBufferException e) {
                throw new ProtobufException(BAD_RECORD_BYTES_MESSAGE, e);
            }
            return this;
        }

        public RecordItemBuilder transaction(Transaction transaction) {
            this.transaction = transaction;
            this.transactionBytes = transaction.toByteArray();
            return this;
        }

        public RecordItemBuilder transactionBytes(byte[] transactionBytes) {
            try {
                this.transactionBytes = transactionBytes;
                this.transaction = Transaction.parseFrom(transactionBytes);
            } catch (InvalidProtocolBufferException e) {
                throw new ProtobufException(BAD_TRANSACTION_BYTES_MESSAGE, e);
            }
            return this;
        }
    }
}
