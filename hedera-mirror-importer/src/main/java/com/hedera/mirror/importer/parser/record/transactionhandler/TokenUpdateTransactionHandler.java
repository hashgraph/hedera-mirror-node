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
import com.hedera.mirror.common.domain.token.Token;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.CustomLog;

@CustomLog
@Named
class TokenUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    private final EntityProperties entityProperties;

    TokenUpdateTransactionHandler(
            EntityIdService entityIdService, EntityListener entityListener, EntityProperties entityProperties) {
        super(entityIdService, entityListener, TransactionType.TOKENUPDATE);
        this.entityProperties = entityProperties;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenUpdate().getToken());
    }

    @Override
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getTokenUpdate();

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasAutoRenewAccount()) {
            // Allow clearing of the autoRenewAccount by allowing it to be set to 0
            entityIdService
                    .lookup(transactionBody.getAutoRenewAccount())
                    .ifPresentOrElse(
                            accountId -> {
                                entity.setAutoRenewAccountId(accountId.getId());
                                recordItem.addEntityId(accountId);
                            },
                            () -> log.error(
                                    RECOVERABLE_ERROR + "Invalid autoRenewAccountId at {}",
                                    recordItem.getConsensusTimestamp()));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpiry()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(transactionBody.getExpiry()));
        }

        if (transactionBody.hasMemo()) {
            entity.setMemo(transactionBody.getMemo().getValue());
        }

        entityListener.onEntity(entity);
        updateToken(entity, recordItem);
    }

    private void updateToken(Entity entity, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenUpdate();
        var token = new Token();
        token.setTimestampLower(recordItem.getConsensusTimestamp());
        token.setTokenId(entity.getId());

        if (transactionBody.hasFeeScheduleKey()) {
            token.setFeeScheduleKey(transactionBody.getFeeScheduleKey().toByteArray());
        }

        if (transactionBody.hasFreezeKey()) {
            token.setFreezeKey(transactionBody.getFreezeKey().toByteArray());
        }

        if (transactionBody.hasKycKey()) {
            token.setKycKey(transactionBody.getKycKey().toByteArray());
        }

        if (!transactionBody.getName().isEmpty()) {
            token.setName(transactionBody.getName());
        }

        if (transactionBody.hasPauseKey()) {
            token.setPauseKey(transactionBody.getPauseKey().toByteArray());
        }

        if (transactionBody.hasSupplyKey()) {
            token.setSupplyKey(transactionBody.getSupplyKey().toByteArray());
        }

        if (!transactionBody.getSymbol().isEmpty()) {
            token.setSymbol(transactionBody.getSymbol());
        }

        if (transactionBody.hasTreasury()) {
            var treasury = EntityId.of(transactionBody.getTreasury());
            token.setTreasuryAccountId(treasury);
            recordItem.addEntityId(treasury);
        }

        if (transactionBody.hasWipeKey()) {
            token.setWipeKey(transactionBody.getWipeKey().toByteArray());
        }

        entityListener.onToken(token);
    }
}
