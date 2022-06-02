package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody.StakedIdCase.STAKEDID_NOT_SET;
import static com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody.StakedIdCase.STAKED_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody.StakedIdCase.STAKED_NODE_ID;

import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import javax.inject.Named;

import com.hedera.mirror.common.converter.AccountIdConverter;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.util.Utility;

@Named
class CryptoUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler<Entity> {

    CryptoUpdateTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                   RecordParserProperties recordParserProperties) {
        super(entityIdService, entityListener, recordParserProperties, TransactionType.CRYPTOUPDATEACCOUNT);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getCryptoUpdateAccount().getAccountIDToUpdate());
    }

    @Override
    @SuppressWarnings("java:S1874")
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getCryptoUpdateAccount();

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpirationTime()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(transactionBody.getExpirationTime()));
        }

        if (transactionBody.hasKey()) {
            entity.setKey(transactionBody.getKey().toByteArray());
        }

        if (transactionBody.hasMaxAutomaticTokenAssociations()) {
            entity.setMaxAutomaticTokenAssociations(transactionBody.getMaxAutomaticTokenAssociations().getValue());
        }

        if (transactionBody.hasMemo()) {
            entity.setMemo(transactionBody.getMemo().getValue());
        }

        if (transactionBody.hasProxyAccountID()) {
            entity.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        if (transactionBody.hasReceiverSigRequiredWrapper()) {
            entity.setReceiverSigRequired(transactionBody.getReceiverSigRequiredWrapper().getValue());
        } else if (transactionBody.getReceiverSigRequired()) {
            // support old transactions
            entity.setReceiverSigRequired(transactionBody.getReceiverSigRequired());
        }

        updateEntityStakingInfo(recordItem.getConsensusTimestamp(), entity, transactionBody);

        entityListener.onEntity(entity);
    }

    private void updateEntityStakingInfo(long consensusTimestamp, Entity entity,
            CryptoUpdateTransactionBody transactionBody) {
        if (transactionBody.hasDeclineReward()) {
            entity.setDeclineReward(transactionBody.getDeclineReward().getValue());
        }

        if (transactionBody.getStakedIdCase() == STAKED_NODE_ID) {
            entity.setStakedNodeId(transactionBody.getStakedNodeId());
            entity.setStakedAccountId(-1L);
        } else if (transactionBody.getStakedIdCase() == STAKED_ACCOUNT_ID) {
            EntityId accountId = EntityId.of(transactionBody.getStakedAccountId());
            entity.setStakedAccountId(AccountIdConverter.INSTANCE.convertToDatabaseColumn(accountId));

            // if the staked account id has changed, we clear the stake period.
            entity.setStakePeriodStart(-1L);

            entity.setStakedNodeId(-1L);
        }

        // If the stake node id or the decline reward value has changed, we start a new stake period.
        if (transactionBody.getStakedIdCase() != STAKEDID_NOT_SET || transactionBody.hasDeclineReward()) {
            entity.setStakePeriodStart(Utility.getEpochDay(consensusTimestamp));
        }
    }
}
