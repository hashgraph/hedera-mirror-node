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

import com.hedera.mirror.importer.domain.Entity;

import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import javax.inject.Named;
import lombok.AllArgsConstructor;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;

@Named
@AllArgsConstructor
public class SystemUndeleteTransactionHandler implements TransactionHandler {

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
    public boolean updatesEntity() {
        return true;
    }

    @Override
    public void updateEntity(Entity entity, RecordItem recordItem) {
        entity.setDeleted(false);
    }
}
