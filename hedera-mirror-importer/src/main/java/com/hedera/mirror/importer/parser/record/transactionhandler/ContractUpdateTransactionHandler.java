package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody.StakedIdCase.STAKEDID_NOT_SET;

import com.hederahashgraph.api.proto.java.ContractID;
import javax.inject.Named;

import com.hedera.mirror.common.domain.entity.AbstractEntity;
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
class ContractUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    ContractUpdateTransactionHandler(EntityIdService entityIdService, EntityListener entityListener,
                                     RecordParserProperties recordParserProperties) {
        super(entityIdService, entityListener, recordParserProperties, TransactionType.CONTRACTUPDATEINSTANCE);
    }

    /**
     * First attempts to extract the contract ID from the receipt, which was populated in HAPI 0.23 for contract update.
     * Otherwise, falls back to checking the transaction body which may contain an EVM address. In case of partial
     * mirror nodes, it's possible the database does not have the mapping for that EVM address in the body, hence the
     * need for prioritizing the receipt.
     *
     * @param recordItem to check
     * @return The contract ID associated with this contract transaction
     */
    @Override
    public EntityId getEntity(RecordItem recordItem) {
        ContractID contractIdBody = recordItem.getTransactionBody().getContractUpdateInstance().getContractID();
        ContractID contractIdReceipt = recordItem.getTransactionRecord().getReceipt().getContractID();
        return entityIdService.lookup(contractIdReceipt, contractIdBody);
    }

    // We explicitly ignore the updated fileID field since hedera nodes do not allow changing the bytecode after create
    @Override
    @SuppressWarnings({"deprecation", "java:S1874"})
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionBody = recordItem.getTransactionBody().getContractUpdateInstance();

        if (transactionBody.hasExpirationTime()) {
            entity.setExpirationTimestamp(DomainUtils.timestampInNanosMax(transactionBody.getExpirationTime()));
        }

        if (transactionBody.hasAutoRenewAccountId()) {
            getAccountId(transactionBody.getAutoRenewAccountId())
                    .map(EntityId::getId)
                    .ifPresent(entity::setAutoRenewAccountId);
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasAdminKey()) {
            entity.setKey(transactionBody.getAdminKey().toByteArray());
        }

        if (transactionBody.hasMaxAutomaticTokenAssociations()) {
            entity.setMaxAutomaticTokenAssociations(transactionBody.getMaxAutomaticTokenAssociations().getValue());
        }

        switch (transactionBody.getMemoFieldCase()) {
            case MEMOWRAPPER:
                entity.setMemo(transactionBody.getMemoWrapper().getValue());
                break;
            case MEMO:
                if (transactionBody.getMemo().length() > 0) {
                    entity.setMemo(transactionBody.getMemo());
                }
                break;
            default:
                break;
        }

        if (transactionBody.hasProxyAccountID()) {
            entity.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        updateStakingInfo(recordItem, entity);
        entityListener.onEntity(entity);
    }

    private void updateStakingInfo(RecordItem recordItem, Entity entity) {
        var transactionBody = recordItem.getTransactionBody().getContractUpdateInstance();
        if (transactionBody.hasDeclineReward()) {
            entity.setDeclineReward(transactionBody.getDeclineReward().getValue());
        }

        switch (transactionBody.getStakedIdCase()) {
            case STAKEDID_NOT_SET:
                break;
            case STAKED_NODE_ID:
                entity.setStakedNodeId(transactionBody.getStakedNodeId());
                entity.setStakedAccountId(AbstractEntity.ACCOUNT_ID_CLEARED);
                break;
            case STAKED_ACCOUNT_ID:
                EntityId accountId = EntityId.of(transactionBody.getStakedAccountId());
                entity.setStakedAccountId(accountId.getId());
                entity.setStakedNodeId(AbstractEntity.NODE_ID_CLEARED);
                break;
        }

        // If the stake node id or the decline reward value has changed, we start a new stake period.
        if (transactionBody.getStakedIdCase() != STAKEDID_NOT_SET || transactionBody.hasDeclineReward()) {
            entity.setStakePeriodStart(Utility.getEpochDay(recordItem.getConsensusTimestamp()));
        }
    }
}
