/*
 * Copyright (C) 2019-2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.RecordParserProperties;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.util.Utility;
import javax.inject.Named;

@Named
class CryptoCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    CryptoCreateTransactionHandler(
            EntityIdService entityIdService,
            EntityListener entityListener,
            RecordParserProperties recordParserProperties) {
        super(
                entityIdService,
                entityListener,
                recordParserProperties,
                TransactionType.CRYPTOCREATEACCOUNT);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getAccountID());
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        transaction.setInitialBalance(
                recordItem.getTransactionBody().getCryptoCreateAccount().getInitialBalance());
    }

    @Override
    @SuppressWarnings("java:S1874")
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var record = recordItem.getRecord();
        var transactionBody = recordItem.getTransactionBody().getCryptoCreateAccount();
        var alias =
                transactionBody.getAlias() != ByteString.EMPTY
                        ? transactionBody.getAlias()
                        : record.getAlias();
        var key = transactionBody.hasKey() ? transactionBody.getKey().toByteArray() : null;
        if (alias != ByteString.EMPTY) {
            var aliasBytes = DomainUtils.toBytes(alias);
            entity.setAlias(aliasBytes);
            entityIdService.notify(entity);

            if (key == null || key.length == 0) {
                entity.setKey(aliasBytes);
            }
        }

        if (key != null && key.length > 0) {
            entity.setKey(key);
        }

        var evmAddress =
                transactionBody.getEvmAddress() != ByteString.EMPTY
                        ? transactionBody.getEvmAddress()
                        : record.getEvmAddress();
        if (evmAddress != ByteString.EMPTY) {
            entity.setEvmAddress(DomainUtils.toBytes(evmAddress));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasProxyAccountID()) {
            entity.setProxyAccountId(EntityId.of(transactionBody.getProxyAccountID()));
        }

        entity.setBalance(0L);
        entity.setMaxAutomaticTokenAssociations(transactionBody.getMaxAutomaticTokenAssociations());
        entity.setMemo(transactionBody.getMemo());
        entity.setReceiverSigRequired(transactionBody.getReceiverSigRequired());

        updateStakingInfo(recordItem, entity);
        entityListener.onEntity(entity);
    }

    private void updateStakingInfo(RecordItem recordItem, Entity entity) {
        var transactionBody = recordItem.getTransactionBody().getCryptoCreateAccount();
        entity.setDeclineReward(transactionBody.getDeclineReward());

        switch (transactionBody.getStakedIdCase()) {
            case STAKEDID_NOT_SET:
                return;
            case STAKED_NODE_ID:
                entity.setStakedNodeId(transactionBody.getStakedNodeId());
                break;
            case STAKED_ACCOUNT_ID:
                EntityId accountId = EntityId.of(transactionBody.getStakedAccountId());
                entity.setStakedAccountId(accountId.getId());
                break;
        }

        entity.setStakePeriodStart(Utility.getEpochDay(recordItem.getConsensusTimestamp()));
    }
}
