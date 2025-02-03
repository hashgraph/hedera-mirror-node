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

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_SCHEDULES_BY_ID;

import com.hedera.hapi.block.stream.output.protoc.CallContractOutput;
import com.hedera.hapi.block.stream.output.protoc.CreateScheduleOutput;
import com.hedera.hapi.block.stream.output.protoc.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.protoc.MapChangeKey;
import com.hedera.hapi.block.stream.output.protoc.MapChangeValue;
import com.hedera.hapi.block.stream.output.protoc.MapDeleteChange;
import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.hapi.block.stream.output.protoc.SignScheduleOutput;
import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.AssessedCustomFee;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Schedule;
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
                transactionResult(recordItem),
                List.of(contractCallTransactionOutput, cryptoTransferTransactionOutput),
                Collections.emptyList());
    }

    public BlockItemBuilder.Builder scheduleCreate() {
        var recordItem = recordItemBuilder.scheduleCreate().build();
        return scheduleCreate(recordItem);
    }

    public BlockItemBuilder.Builder scheduleCreate(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var scheduleId = transactionRecord.getReceipt().getScheduleID();
        var transactionId = transactionRecord.getReceipt().getScheduledTransactionID();
        var transactionOutput = TransactionOutput.newBuilder()
                .setCreateSchedule(CreateScheduleOutput.newBuilder()
                        .setScheduleId(scheduleId)
                        .setScheduledTransactionId(transactionId)
                        .build())
                .build();

        var stateChange = StateChange.newBuilder()
                .setStateId(STATE_ID_SCHEDULES_BY_ID.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setScheduleIdKey(scheduleId)))
                .build();
        var stateChanges =
                StateChanges.newBuilder().addStateChanges(stateChange).build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(transactionOutput),
                List.of(stateChanges));
    }

    public BlockItemBuilder.Builder scheduleDelete() {
        var recordItem = recordItemBuilder.scheduleDelete().build();
        return scheduleDelete(recordItem);
    }

    public BlockItemBuilder.Builder scheduleDelete(RecordItem recordItem) {
        var scheduleId = recordItem.getTransactionBody().getScheduleDelete().getScheduleID();
        var stateChangeDelete = StateChange.newBuilder()
                .setMapDelete(MapDeleteChange.newBuilder()
                        .setKey(MapChangeKey.newBuilder().setScheduleIdKey(scheduleId)))
                .build();
        var stateChangeUpdate = StateChange.newBuilder()
                .setStateId(STATE_ID_SCHEDULES_BY_ID.getNumber())
                .setMapUpdate(MapUpdateChange.newBuilder()
                        .setValue(MapChangeValue.newBuilder()
                                .setScheduleValue(Schedule.newBuilder()
                                        .setScheduleId(scheduleId)
                                        .build())
                                .build()))
                .build();

        var stateChanges = StateChanges.newBuilder()
                .addStateChanges(stateChangeDelete)
                .addStateChanges(stateChangeUpdate)
                .build();

        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                Collections.emptyList(),
                List.of(stateChanges));
    }

    public BlockItemBuilder.Builder scheduleSign() {
        var recordItem = recordItemBuilder.scheduleSign().build();
        return scheduleSign(recordItem);
    }

    public BlockItemBuilder.Builder scheduleSign(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionId = transactionRecord.getReceipt().getScheduledTransactionID();
        var transactionOutput = TransactionOutput.newBuilder()
                .setSignSchedule(SignScheduleOutput.newBuilder()
                        .setScheduledTransactionId(transactionId)
                        .build())
                .build();
        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(),
                transactionResult(recordItem),
                List.of(transactionOutput),
                Collections.emptyList());
    }

    public BlockItemBuilder.Builder unknown() {
        var recordItem = recordItemBuilder.unknown().build();
        return unknown(recordItem);
    }

    public BlockItemBuilder.Builder unknown(RecordItem recordItem) {
        return new BlockItemBuilder.Builder(
                recordItem.getTransaction(), transactionResult(recordItem), List.of(), Collections.emptyList());
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

        var firstChange = StateChange.newBuilder()
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .setStateId(1)
                .build();

        var secondChange = StateChange.newBuilder().setStateId(STATE_FILES_ID).build();

        var thirdChange = StateChange.newBuilder()
                .setStateId(STATE_FILES_ID)
                .setMapUpdate(MapUpdateChange.newBuilder().build())
                .build();

        var fourtChange = StateChange.newBuilder()
                .setMapUpdate(mapUpdate)
                .setStateId(STATE_FILES_ID)
                .build();

        var changes = List.of(firstChange, secondChange, thirdChange, fourtChange);

        return StateChanges.newBuilder().addAllStateChanges(changes).build();
    }

    private AssessedCustomFee.Builder assessedCustomFees() {
        return AssessedCustomFee.newBuilder()
                .setAmount(1L)
                .addEffectivePayerAccountId(recordItemBuilder.accountId())
                .setFeeCollectorAccountId(recordItemBuilder.accountId())
                .setTokenId(recordItemBuilder.tokenId());
    }

    private Timestamp timestamp(long consensusTimestamp) {
        var instant = Instant.ofEpochSecond(0, consensusTimestamp);
        return Utility.instantToTimestamp(instant);
    }

    private TransactionResult transactionResult(RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var timestamp = timestamp(recordItem.getConsensusTimestamp());
        return transactionResult(transactionRecord, timestamp).build();
    }

    private TransactionResult.Builder transactionResult(
            TransactionRecord transactionRecord, Timestamp consensusTimestamp) {
        var builder = TransactionResult.newBuilder();
        if (transactionRecord.hasParentConsensusTimestamp()) {
            builder.setParentConsensusTimestamp(transactionRecord.getParentConsensusTimestamp());
        }
        if (transactionRecord.hasScheduleRef()) {
            builder.setScheduleRef(transactionRecord.getScheduleRef());
        }

        return builder.addAllPaidStakingRewards(transactionRecord.getPaidStakingRewardsList())
                .addAllTokenTransferLists(transactionRecord.getTokenTransferListsList())
                .setConsensusTimestamp(consensusTimestamp)
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
