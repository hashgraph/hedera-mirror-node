package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.importer.domain.AbstractEntity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractEntityCrudTransactionHandler<T extends AbstractEntity> implements TransactionHandler {

    protected final EntityListener entityListener;

    @Getter
    private final TransactionTypeEnum type;

    @Override
    public final void updateTransaction(Transaction transaction, RecordItem recordItem) {
        doUpdateTransaction(transaction, recordItem);
        EntityId entityId = transaction.getEntityId();
        EntityOperation entityOperation = type.getEntityOperation();

        if (entityOperation != EntityOperation.NONE && !EntityId.isEmpty(entityId) && recordItem.isSuccessful()) {
            updateEntity(entityId, recordItem);
        }
    }

    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
    }

    protected final void updateEntity(EntityId entityId, RecordItem recordItem) {
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        T entity = entityId.toEntity();
        EntityOperation entityOperation = type.getEntityOperation();

        if (entityOperation == EntityOperation.CREATE) {
            entity.setCreatedTimestamp(consensusTimestamp);
            entity.setDeleted(false);
        } else if (entityOperation == EntityOperation.UPDATE) {
            entity.setDeleted(false);
        } else if (entityOperation == EntityOperation.DELETE) {
            entity.setDeleted(true);
        }

        entity.setModifiedTimestamp(consensusTimestamp);
        doUpdateEntity(entity, recordItem);
    }

    protected abstract void doUpdateEntity(T entity, RecordItem recordItem);
}
