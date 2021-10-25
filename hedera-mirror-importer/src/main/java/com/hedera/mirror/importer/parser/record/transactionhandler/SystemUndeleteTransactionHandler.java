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

import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.AbstractEntity;
import com.hedera.mirror.importer.domain.Contract;
import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Named
class SystemUndeleteTransactionHandler extends AbstractEntityCrudTransactionHandler<AbstractEntity> {

    SystemUndeleteTransactionHandler(EntityListener entityListener) {
        super(entityListener, TransactionTypeEnum.SYSTEMUNDELETE);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        SystemUndeleteTransactionBody systemUndelete = recordItem.getTransactionBody().getSystemUndelete();
        if (systemUndelete.hasContractID()) {
            return EntityId.of(systemUndelete.getContractID());
        } else if (systemUndelete.hasFileID()) {
            return EntityId.of(systemUndelete.getFileID());
        }
        return null;
    }

    @Override
    protected void doUpdateEntity(AbstractEntity entity, RecordItem recordItem) {
        if (entity instanceof Contract) {
            entityListener.onContract((Contract) entity);
        } else {
            entityListener.onEntity((Entity) entity);
        }
    }
}
