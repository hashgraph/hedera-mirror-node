/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import com.hedera.mirror.common.domain.token.TokenAirdropStateEnum;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class TokenCancelAirdropTransactionHandler extends AbstractTransactionHandler {

    private final EntityProperties entityProperties;
    private final TokenUpdateAirdropTransactionHandler tokenUpdateAirdropTransactionHandler;

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokenAirdrops() || !recordItem.isSuccessful()) {
            return;
        }

        var pendingAirdropIds =
                recordItem.getTransactionBody().getTokenCancelAirdrop().getPendingAirdropsList();
        tokenUpdateAirdropTransactionHandler.doUpdateTransaction(
                recordItem, TokenAirdropStateEnum.CANCELLED, pendingAirdropIds);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        var pendingAirdropIds =
                recordItem.getTransactionBody().getTokenCancelAirdrop().getPendingAirdropsList();
        return tokenUpdateAirdropTransactionHandler.getEntity(pendingAirdropIds);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENCANCELAIRDROP;
    }
}
