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
import com.hedera.mirror.common.domain.contract.ContractTransaction;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.codec.binary.Hex;
import org.springframework.data.util.Version;

@Builder(buildMethodName = "buildInternal")
@AllArgsConstructor(access = PRIVATE)
@CustomLog
@Value
public class RecordItem implements StreamItem {

    static final String BAD_TRANSACTION_BYTES_MESSAGE = "Failed to parse transaction bytes";
    static final String BAD_RECORD_BYTES_MESSAGE = "Failed to parse record bytes";
    static final String BAD_TRANSACTION_BODY_BYTES_MESSAGE = "Error parsing transactionBody from transaction";

    // Final fields
    @Builder.Default
    private final Version hapiVersion = RecordFile.HAPI_VERSION_NOT_SET;

    private final long consensusTimestamp;
    private final RecordItem parent;
    private final EntityId payerAccountId;
    private final RecordItem previous;
    private final SignatureMap signatureMap;
    private final boolean successful;
    private final Transaction transaction;
    private final TransactionBody transactionBody;
    private final int transactionIndex;
    private final TransactionRecord transactionRecord;
    private final int transactionType;

    @Getter(PRIVATE)
    private final AtomicInteger logIndex = new AtomicInteger(0);

    @NonFinal
    @Setter
    private Predicate<EntityId> contractTransactionPredicate;

    @NonFinal
    private Map<Long, ContractTransaction> contractTransactions;

    @Getter(AccessLevel.NONE)
    @NonFinal
    private EntityTransaction.EntityTransactionBuilder entityTransactionBuilder;

    @NonFinal
    @Setter
    private Predicate<EntityId> entityTransactionPredicate;

    @NonFinal
    private Map<Long, EntityTransaction> entityTransactions;

    @NonFinal
    @Setter
    private EthereumTransaction ethereumTransaction;

    @Builder.Default
    @NonFinal
    @Setter
    private List<TransactionSidecarRecord> sidecarRecords = Collections.emptyList();

    public void addContractTransaction(EntityId entityId) {
        if (contractTransactionPredicate == null || !contractTransactionPredicate.test(entityId)) {
            return;
        }
        getContractTransactions().computeIfAbsent(entityId.getId(), key -> ContractTransaction.builder()
                .entityId(key)
                .payerAccountId(payerAccountId.getId())
                .consensusTimestamp(consensusTimestamp)
                .build());
    }

    public void addEntityId(EntityId entityId) {
        if (entityTransactionPredicate == null || !entityTransactionPredicate.test(entityId)) {
            return;
        }

        if (entityTransactionBuilder == null) {
            entityTransactionBuilder = EntityTransaction.builder()
                    .consensusTimestamp(consensusTimestamp)
                    .payerAccountId(payerAccountId)
                    .result(getTransactionStatus())
                    .type(transactionType);
        }

        getEntityTransactions().computeIfAbsent(entityId.getId(), id -> entityTransactionBuilder
                .entityId(id)
                .build());
    }

    public Map<Long, EntityTransaction> getEntityTransactions() {
        if (entityTransactions == null) {
            entityTransactions = new HashMap<>();
        }
        return entityTransactions;
    }

    public int getAndIncrementLogIndex() {
        return logIndex.getAndIncrement();
    }

    public int getTransactionStatus() {
        return transactionRecord.getReceipt().getStatusValue();
    }

    public boolean isChild() {
        return transactionRecord.hasParentConsensusTimestamp();
    }

    public boolean isInvalidIdError() {
        return switch (transactionRecord.getReceipt().getStatus()) {
            case INVALID_ACCOUNT_ID,
                    INVALID_CONTRACT_ID,
                    INVALID_FILE_ID,
                    INVALID_SCHEDULE_ID,
                    INVALID_TOKEN_ID,
                    INVALID_TOPIC_ID -> true;
            default -> false;
        };
    }

    // Whether this is a top level, user submitted transaction that could possibly trigger other internal transactions.
    public boolean isTopLevel() {
        return transactionRecord.getTransactionID().getNonce() == 0;
    }

    /**
     * Check whether ethereum transaction exist in the record item and returns it hash, if not return 32-byte
     * representation of the transaction hash
     *
     * @return 32-byte transaction hash of this record item
     */
    public byte[] getTransactionHash() {
        return Optional.ofNullable(getEthereumTransaction())
                .map(EthereumTransaction::getHash)
                .orElseGet(() -> Arrays.copyOfRange(
                        DomainUtils.toBytes(getTransactionRecord().getTransactionHash()), 0, 32));
    }

    private Map<Long, ContractTransaction> getContractTransactions() {
        if (contractTransactions == null) {
            contractTransactions = new HashMap<>();
        }

        return contractTransactions;
    }

    public Collection<ContractTransaction> populateContractTransactions() {
        if (contractTransactions == null || contractTransactions.isEmpty()) {
            return Collections.emptyList();
        }
        var ids = new ArrayList<>(contractTransactions.keySet());
        contractTransactions.values().forEach(contractTransaction -> contractTransaction.setContractIds(ids));
        return contractTransactions.values();
    }

    public static class RecordItemBuilder {

        public RecordItem build() {
            parseTransaction();
            this.consensusTimestamp = DomainUtils.timestampInNanosMax(transactionRecord.getConsensusTimestamp());
            this.parent = parseParent();
            this.payerAccountId = EntityId.of(transactionBody.getTransactionID().getAccountID());
            this.successful = parseSuccess();
            this.transactionType = parseTransactionType(transactionBody);
            return buildInternal();
        }

        private RecordItem parseParent() {
            // set parent, parent-child items are assured to exist in sequential order of [Parent, Child1,..., ChildN]
            if (transactionRecord.hasParentConsensusTimestamp() && previous != null) {
                var parentTimestamp = transactionRecord.getParentConsensusTimestamp();
                if (parentTimestamp.equals(previous.transactionRecord.getConsensusTimestamp())) {
                    return previous;
                } else if (previous.parent != null
                        && parentTimestamp.equals(previous.parent.transactionRecord.getConsensusTimestamp())) {
                    // check older siblings parent, if child count is > 1 this prevents having to search to parent
                    return previous.parent;
                }
            }
            return this.parent;
        }

        private boolean parseSuccess() {
            // A child is only successful if its parent is as well. Consensus nodes have had issues in the past where
            // that
            // invariant did not hold true and children contract calls were not reverted on failure.
            if (parent != null && !parent.isSuccessful()) {
                return false;
            }

            var status = transactionRecord.getReceipt().getStatus();
            return status == ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED
                    || status == ResponseCodeEnum.SUCCESS
                    || status == ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
        }

        /**
         * Parses the transaction into separate TransactionBody and SignatureMap objects. Necessary since the
         * Transaction payload has changed incompatibly multiple times over its lifetime.
         * <p>
         * Not possible to check the existence of bodyBytes or signedTransactionBytes fields since there are no
         * 'hasBodyBytes()' or 'hasSignedTransactionBytes()` methods. If unset, they return empty ByteString which
         * always parses successfully to an empty TransactionBody. However, every transaction should have a valid
         * (non-empty) TransactionBody.
         */
        @SuppressWarnings("deprecation")
        private void parseTransaction() {
            try {
                if (!transaction.getSignedTransactionBytes().equals(ByteString.EMPTY)) {
                    var signedTransaction = SignedTransaction.parseFrom(transaction.getSignedTransactionBytes());
                    this.transactionBody = TransactionBody.parseFrom(signedTransaction.getBodyBytes());
                    this.signatureMap = signedTransaction.getSigMap();
                } else if (!transaction.getBodyBytes().equals(ByteString.EMPTY)) {
                    this.transactionBody = TransactionBody.parseFrom(transaction.getBodyBytes());
                    this.signatureMap = transaction.getSigMap();
                } else if (transaction.hasBody()) {
                    this.transactionBody = transaction.getBody();
                    this.signatureMap = transaction.getSigMap();
                }

                if (transactionBody == null || signatureMap == null) {
                    throw new ProtobufException(BAD_TRANSACTION_BODY_BYTES_MESSAGE);
                }
            } catch (InvalidProtocolBufferException e) {
                throw new ProtobufException(BAD_TRANSACTION_BODY_BYTES_MESSAGE, e);
            }
        }

        /**
         * Because body.getDataCase() can return null for unknown transaction types, we instead get oneof generically
         *
         * @return The protobuf ID that represents the transaction type
         */
        private int parseTransactionType(TransactionBody body) {
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
    }
}
