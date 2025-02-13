/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.block.transformer;

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_SCHEDULES_BY_ID;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@SuppressWarnings("java:S3776")
@Named
final class ScheduleCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(
            BlockItem blockItem, TransactionBody transactionBody, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        var receiptBuilder = transactionRecordBuilder.getReceiptBuilder();
        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasCreateSchedule()) {
                var output = transactionOutput.getCreateSchedule();
                if (output.hasScheduledTransactionId()) {
                    receiptBuilder.setScheduledTransactionID(output.getScheduledTransactionId());
                    break;
                }
            }
        }

        for (var stateChanges : blockItem.stateChanges()) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (stateChange.getStateId() == STATE_ID_SCHEDULES_BY_ID.getNumber() && stateChange.hasMapUpdate()) {
                    var key = stateChange.getMapUpdate().getKey();
                    if (key.hasScheduleIdKey()) {
                        receiptBuilder.setScheduleID(key.getScheduleIdKey());
                        return;
                    }
                }
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.SCHEDULECREATE;
    }
}
