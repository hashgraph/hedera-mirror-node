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

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.util.Utility;

@Named
public class TokenCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    public TokenCreateTransactionHandler() {
        super(true);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getTokenID());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        TokenCreateTransactionBody tokenCreateTransactionBody = recordItem.getTransactionBody().getTokenCreation();
        if (tokenCreateTransactionBody.hasAdminKey()) {
            entity.setKey(tokenCreateTransactionBody.getAdminKey().toByteArray());
        }

        if (tokenCreateTransactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(tokenCreateTransactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (tokenCreateTransactionBody.hasExpiry()) {
            entity.setExpirationTimestamp(Utility.timestampInNanosMax(tokenCreateTransactionBody.getExpiry()));
        }

        entity.setMemo(tokenCreateTransactionBody.getMemo());
    }

    @Override
    protected EntityId getAutoRenewAccount(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenCreation().getAutoRenewAccount());
    }
}
