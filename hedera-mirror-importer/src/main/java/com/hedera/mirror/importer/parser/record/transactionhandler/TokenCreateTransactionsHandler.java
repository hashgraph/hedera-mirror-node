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

import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import javax.inject.Named;
import lombok.AllArgsConstructor;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

@Named
@AllArgsConstructor
public class TokenCreateTransactionsHandler implements TransactionHandler {

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getTokenID());
    }

    @Override
    public boolean updatesEntity() {
        return true;
    }

    @Override
    public void updateEntity(Entities entity, RecordItem recordItem) {
        TokenCreateTransactionBody tokenCreateTransactionBody = recordItem.getTransactionBody().getTokenCreation();
        if (tokenCreateTransactionBody.hasAdminKey()) {
            entity.setKey(tokenCreateTransactionBody.getAdminKey().toByteArray());
        }

        if (tokenCreateTransactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(tokenCreateTransactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (tokenCreateTransactionBody.hasExpiry()) {
            entity.setExpiryTimeNs(Utility.timestampInNanosMax(tokenCreateTransactionBody.getExpiry()));
        }

        entity.setMemo(tokenCreateTransactionBody.getMemo());
    }

    @Override
    public EntityId getAutoRenewAccount(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenCreation().getAutoRenewAccount());
    }
}
