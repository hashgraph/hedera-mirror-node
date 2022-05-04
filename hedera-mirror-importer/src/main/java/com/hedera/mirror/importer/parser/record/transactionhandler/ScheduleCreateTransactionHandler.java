package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import javax.inject.Named;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.schedule.Schedule;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@Named
class ScheduleCreateTransactionHandler extends AbstractEntityCrudTransactionHandler<Entity> {

    private final EntityProperties entityProperties;

    ScheduleCreateTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                     EntityProperties entityProperties, RecordParserProperties recordParserProperties) {
        super(entityIdService, entityListener, recordParserProperties, TransactionType.SCHEDULECREATE);
        this.entityProperties = entityProperties;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getScheduleID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getScheduleCreate();

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        entity.setMemo(transactionBody.getMemo());
        entityListener.onEntity(entity);
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful() || !entityProperties.getPersist().isSchedules()) {
            return;
        }

        var body = recordItem.getTransactionBody().getScheduleCreate();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var creatorAccount = recordItem.getPayerAccountId();
        var expirationTime = body.hasExpirationTime() ? DomainUtils.timestampInNanosMax(body.getExpirationTime()) : null;
        var payerAccount = body.hasPayerAccountID() ? EntityId.of(body.getPayerAccountID()) : creatorAccount;
        var scheduleId = EntityId.of(recordItem.getRecord().getReceipt().getScheduleID());

        Schedule schedule = new Schedule();
        schedule.setConsensusTimestamp(consensusTimestamp);
        schedule.setCreatorAccountId(creatorAccount);
        schedule.setExpirationTime(expirationTime);
        schedule.setPayerAccountId(payerAccount);
        schedule.setScheduleId(scheduleId);
        schedule.setTransactionBody(body.getScheduledTransactionBody().toByteArray());
        schedule.setWaitForExpiry(body.getWaitForExpiry());
        entityListener.onSchedule(schedule);
    }
}
