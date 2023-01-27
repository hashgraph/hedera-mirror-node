package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityOperation;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.exception.AliasNotFoundException;
import com.hedera.mirror.importer.parser.PartialDataAction;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractEntityCrudTransactionHandler implements TransactionHandler {

    protected final EntityIdService entityIdService;

    protected final EntityListener entityListener;

    protected final RecordParserProperties recordParserProperties;

    @Getter
    private final TransactionType type;

    protected Optional<EntityId> getAccountId(AccountID accountId) {
        try {
            return Optional.of(entityIdService.lookup(accountId));
        } catch (AliasNotFoundException ex) {
            if (recordParserProperties.getPartialDataAction() == PartialDataAction.SKIP) {
                return Optional.empty();
            }
            throw ex;
        }
    }

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
        Entity entity = entityId.toEntity();
        EntityOperation entityOperation = type.getEntityOperation();

        if (entityOperation == EntityOperation.CREATE) {
            entity.setCreatedTimestamp(consensusTimestamp);
            entity.setDeleted(false);
        } else if (entityOperation == EntityOperation.UPDATE) {
            entity.setDeleted(false);
        } else if (entityOperation == EntityOperation.DELETE) {
            entity.setDeleted(true);
        }

        entity.setTimestampLower(consensusTimestamp);
        doUpdateEntity(entity, recordItem);
    }

    protected abstract void doUpdateEntity(Entity entity, RecordItem recordItem);
}
