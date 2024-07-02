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

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class TokenRejectTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;

    @Override
    protected void addCommonEntityIds(Transaction transaction, RecordItem recordItem) {
        super.addCommonEntityIds(transaction, recordItem);
        var rejections = recordItem.getTransactionBody().getTokenReject().getRejectionsList();
        for (var rejection : rejections) {
            var tokenId = rejection.hasFungibleToken()
                    ? rejection.getFungibleToken()
                    : rejection.getNft().getTokenID();
            recordItem.addEntityId(EntityId.of(tokenId));
        }
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var owner = recordItem.getTransactionBody().getTokenReject().getOwner();
        var ownerId = entityIdService.lookup(owner).orElse(EntityId.EMPTY);
        return !EntityId.isEmpty(ownerId) ? ownerId : recordItem.getPayerAccountId();
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENREJECT;
    }
}
