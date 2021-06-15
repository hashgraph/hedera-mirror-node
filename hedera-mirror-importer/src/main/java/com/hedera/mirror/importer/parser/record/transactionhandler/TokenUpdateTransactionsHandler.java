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

import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import javax.inject.Named;
import lombok.AllArgsConstructor;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

@Named
@AllArgsConstructor
public class TokenUpdateTransactionsHandler implements TransactionHandler {

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
    }

    @Override
    public boolean updatesEntity() {
        return true;
    }

    @Override
    public void updateEntity(Entity entity, RecordItem recordItem) {
        TokenUpdateTransactionBody tokenUpdateTransactionBody = recordItem.getTransactionBody().getTokenUpdate();
        if (tokenUpdateTransactionBody.hasAdminKey()) {
            entity.setKey(tokenUpdateTransactionBody.getAdminKey().toByteArray());
        }

        if (tokenUpdateTransactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(tokenUpdateTransactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (tokenUpdateTransactionBody.hasExpiry()) {
            entity.setExpirationTimestamp(Utility.timestampInNanosMax(tokenUpdateTransactionBody.getExpiry()));
        }

        if (tokenUpdateTransactionBody.hasMemo()) {
            entity.setMemo(tokenUpdateTransactionBody.getMemo().getValue());
        }
    }

    @Override
    public EntityId getAutoRenewAccount(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getAutoRenewAccount());
    }
}
