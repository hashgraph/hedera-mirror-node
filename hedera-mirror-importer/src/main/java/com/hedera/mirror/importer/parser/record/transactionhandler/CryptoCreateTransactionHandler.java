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

import static com.hedera.mirror.common.util.DomainUtils.EVM_ADDRESS_LENGTH;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.util.Utility;
import jakarta.inject.Named;
import org.apache.commons.lang3.ArrayUtils;

@Named
class CryptoCreateTransactionHandler extends AbstractEntityCrudTransactionHandler {

    CryptoCreateTransactionHandler(EntityIdService entityIdService, EntityListener entityListener) {
        super(entityIdService, entityListener, TransactionType.CRYPTOCREATEACCOUNT);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionRecord().getReceipt().getAccountID());
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        transaction.setInitialBalance(
                recordItem.getTransactionBody().getCryptoCreateAccount().getInitialBalance());
    }

    @Override
    @SuppressWarnings({"deprecation", "java:S1874"})
    protected void doUpdateEntity(Entity entity, RecordItem recordItem) {
        var transactionRecord = recordItem.getTransactionRecord();
        var transactionBody = recordItem.getTransactionBody().getCryptoCreateAccount();
        var alias = DomainUtils.toBytes(
                transactionRecord.getAlias() != ByteString.EMPTY
                        ? transactionRecord.getAlias()
                        : transactionBody.getAlias());
        boolean emptyAlias = ArrayUtils.isEmpty(alias);
        var key = transactionBody.hasKey() ? transactionBody.getKey().toByteArray() : null;
        boolean emptyKey = ArrayUtils.isEmpty(key);
        if (!emptyAlias) {
            entity.setAlias(alias);
            entityIdService.notify(entity);
            if (emptyKey && alias.length > EVM_ADDRESS_LENGTH) {
                entity.setKey(alias);
            }
        }

        if (!emptyKey) {
            entity.setKey(key);
        }

        var evmAddress = transactionRecord.getEvmAddress();
        if (evmAddress != ByteString.EMPTY) {
            entity.setEvmAddress(DomainUtils.toBytes(evmAddress));
        } else if (!emptyAlias) {
            entity.setEvmAddress(Utility.aliasToEvmAddress(alias));
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasProxyAccountID()) {
            var proxyAccountId = EntityId.of(transactionBody.getProxyAccountID());
            entity.setProxyAccountId(proxyAccountId);
            recordItem.addEntityId(proxyAccountId);
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
            case STAKEDID_NOT_SET -> {
                return;
            }
            case STAKED_NODE_ID -> entity.setStakedNodeId(transactionBody.getStakedNodeId());
            case STAKED_ACCOUNT_ID -> {
                var accountId = EntityId.of(transactionBody.getStakedAccountId());
                entity.setStakedAccountId(accountId.getId());
                recordItem.addEntityId(accountId);
            }
        }

        entity.setStakePeriodStart(Utility.getEpochDay(recordItem.getConsensusTimestamp()));
    }
}
