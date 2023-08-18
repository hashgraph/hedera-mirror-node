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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
class CryptoDeleteTransactionHandler extends AbstractEntityCrudTransactionHandler {

    CryptoDeleteTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.CRYPTODELETE);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getCryptoDelete().getDeleteAccountID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getCryptoDelete();
        var obtainerId =
                entityIdService.lookup(transactionBody.getTransferAccountID()).orElse(EntityId.EMPTY);
        if (EntityId.isEmpty(obtainerId)) {
            log.error(
                    RECOVERABLE_ERROR + "Unable to lookup ObtainerId at consensusTimestamp {}",
                    recordItem.getConsensusTimestamp());
        } else {
            entity.setObtainerId(obtainerId);
        }

        entityListener.onEntity(entity);
        recordItem.addEntityId(obtainerId);
    }
}
