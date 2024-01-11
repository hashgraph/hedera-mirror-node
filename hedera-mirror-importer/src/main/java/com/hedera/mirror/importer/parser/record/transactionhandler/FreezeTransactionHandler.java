/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static java.time.ZoneOffset.UTC;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.NetworkFreeze;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import jakarta.inject.Named;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class FreezeTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getFreeze().getUpdateFile());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.FREEZE;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        long startTime;
        Long endTime = null;
        var body = recordItem.getTransactionBody().getFreeze();
        var fileId = body.hasUpdateFile() ? EntityId.of(body.getUpdateFile()) : null;

        if (body.hasStartTime()) {
            startTime = DomainUtils.timestampInNanosMax(body.getStartTime());
        } else {
            var consensusTime = Instant.ofEpochSecond(0L, recordItem.getConsensusTimestamp());
            var startOfDay = LocalDate.ofInstant(consensusTime, UTC).atStartOfDay();

            var startDateTime = startOfDay.withHour(body.getStartHour()).withMinute(body.getStartMin());
            startTime = DomainUtils.convertToNanosMax(startDateTime.toInstant(UTC));

            var endDateTime = startOfDay.withHour(body.getEndHour()).withMinute(body.getEndMin());

            // The freeze starts in one day, but ends in another
            if (body.getStartHour() > body.getEndHour()) {
                endDateTime = endDateTime.plusDays(1);
            }

            endTime = DomainUtils.convertToNanosMax(endDateTime.toInstant(UTC));
        }

        var networkFreeze = new NetworkFreeze();
        networkFreeze.setConsensusTimestamp(recordItem.getConsensusTimestamp());
        networkFreeze.setEndTime(endTime);
        networkFreeze.setFileHash(DomainUtils.toBytes(body.getFileHash()));
        networkFreeze.setFileId(fileId);
        networkFreeze.setPayerAccountId(recordItem.getPayerAccountId());
        networkFreeze.setStartTime(startTime);
        networkFreeze.setType(body.getFreezeTypeValue());
        entityListener.onNetworkFreeze(networkFreeze);
    }
}
