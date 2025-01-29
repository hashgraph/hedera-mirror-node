/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.domain;

import com.hedera.hapi.block.stream.output.protoc.CallContractOutput;
import com.hedera.hapi.block.stream.output.protoc.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

/**
 * Generates typical protobuf request and response objects with all fields populated.
 */
@Named
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BlockItemBuilder {
    private static final int STATE_FILES_ID = 6;

    private final RecordItemBuilder recordItemBuilder = new RecordItemBuilder();

    public BlockItemBuilder.Builder cryptoTransfer() {
        var recordItem = recordItemBuilder.cryptoTransfer().build();
        return cryptoTransfer(recordItem);
    }

    public BlockItemBuilder.Builder cryptoTransfer(RecordItem recordItem) {
        var instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        var timestamp = Utility.instantToTimestamp(instant);
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionResult = transactionResult(transactionRecord, timestamp).build();

        var contractCallTransactionOutput = TransactionOutput.newBuilder()
                .setContractCall(CallContractOutput.newBuilder()
                        .setContractCallResult(recordItemBuilder.contractFunctionResult())
                        .build())
                .build();
        var cryptoTransferTransactionOutput = TransactionOutput.newBuilder()
                .setCryptoTransfer(CryptoTransferOutput.newBuilder()
                        .addAssessedCustomFees(assessedCustomFees())
                        .build())
                .build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult,
                List.of(contractCallTransactionOutput, cryptoTransferTransactionOutput),
                Collections.emptyList());
    }

    public BlockItemBuilder.Builder unknown() {
        var recordItem = recordItemBuilder.unknown().build();
        return unknown(recordItem);
    }

    public BlockItemBuilder.Builder unknown(RecordItem recordItem) {
        var instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        var timestamp = Utility.instantToTimestamp(instant);
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionResult = transactionResult(transactionRecord, timestamp).build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult, List.of(), Collections.emptyList());
    }

    public Builder fileAppend(RecordItem recordItem) {
        var instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        var timestamp = Utility.instantToTimestamp(instant);
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionResult = transactionResult(transactionRecord, timestamp).build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult, List.of(), Collections.emptyList());
    }

    public Builder fileDelete(RecordItem recordItem) {
        var instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        var timestamp = Utility.instantToTimestamp(instant);
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionResult = transactionResult(transactionRecord, timestamp).build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult, List.of(), Collections.emptyList());
    }

    public Builder fileCreate(RecordItem recordItem) {
        var instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        var timestamp = Utility.instantToTimestamp(instant);
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionResult = transactionResult(transactionRecord, timestamp).build();

        var stateChanges = buildFileIdStateChanges(recordItem);

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult, List.of(), List.of(stateChanges));
    }

    public Builder fileUpdate(RecordItem recordItem) {
        var instant = Instant.ofEpochSecond(0, recordItem.getConsensusTimestamp());
        var timestamp = Utility.instantToTimestamp(instant);
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionResult = transactionResult(transactionRecord, timestamp).build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult, List.of(), Collections.emptyList());
    }

    private static StateChanges buildFileIdStateChanges(RecordItem recordItem) {
        var id = recordItem.getTransactionRecord().getReceipt().getFileID().getFileNum();
        var fileId = FileID.newBuilder().setFileNum(id).build();
        var key = MapChangeKey.newBuilder().setFileIdKey(fileId).build();
        var mapUpdate = MapUpdateChange.newBuilder().setKey(key).build();
        var change = StateChange.newBuilder().setMapUpdate(mapUpdate).setStateId(STATE_FILES_ID).build();

        return StateChanges.newBuilder().addStateChanges(change).build();
    }

    private AssessedCustomFee.Builder assessedCustomFees() {
        return AssessedCustomFee.newBuilder()
                .setAmount(1L)
                .addEffectivePayerAccountId(recordItemBuilder.accountId())
                .setFeeCollectorAccountId(recordItemBuilder.accountId())
                .setTokenId(recordItemBuilder.tokenId());
    }

    private TransactionResult.Builder transactionResult(
            TransactionRecord transactionRecord, Timestamp consensusTimestamp) {
        return TransactionResult.newBuilder()
                .addAllPaidStakingRewards(transactionRecord.getPaidStakingRewardsList())
                .addAllTokenTransferLists(transactionRecord.getTokenTransferListsList())
                .setConsensusTimestamp(consensusTimestamp)
                .setParentConsensusTimestamp(transactionRecord.getParentConsensusTimestamp())
                .setScheduleRef(transactionRecord.getScheduleRef())
                .setTransferList(transactionRecord.getTransferList())
                .setTransactionFeeCharged(transactionRecord.getTransactionFee())
                .setStatus(transactionRecord.getReceipt().getStatus());
    }

    public class Builder {
        private final Transaction transaction;
        private final List<TransactionOutput> transactionOutputs;
        private final TransactionResult transactionResult;
        private final List<StateChanges> stateChanges;

        private Builder(
                Transaction transaction,
                TransactionResult transactionResult,
                List<TransactionOutput> transactionOutputs,
                List<StateChanges> stateChanges) {
            this.stateChanges = stateChanges;
            this.transaction = transaction;
            this.transactionOutputs = transactionOutputs;
            this.transactionResult = transactionResult;
        }

        public BlockItem build() {
            return BlockItem.builder()
                    .transaction(transaction)
                    .transactionResult(transactionResult)
                    .transactionOutput(transactionOutputs)
                    .stateChanges(stateChanges)
                    .build();
        }
    }
}
