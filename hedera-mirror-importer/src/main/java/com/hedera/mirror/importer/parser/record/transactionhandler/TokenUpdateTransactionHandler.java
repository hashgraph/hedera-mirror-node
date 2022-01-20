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

import com.hedera.mirror.common.util.DomainUtils;

import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import javax.inject.Named;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.NftTransferId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.NftRepository;

@Named
class TokenUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler<Entity> {

    private final NftRepository nftRepository;

    TokenUpdateTransactionHandler(EntityListener entityListener, NftRepository nftRepository) {
        super(entityListener, TransactionType.TOKENUPDATE);
        this.nftRepository = nftRepository;
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
            entity.setAutoRenewAccountId(EntityId.of(transactionBody.getAutoRenewAccount()));
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

        updateTreasury(recordItem);
        entityListener.onEntity(entity);
    }

    private void updateTreasury(RecordItem recordItem) {
        var payerAccountId = EntityId.of(
                recordItem.getTransactionBody().getTransactionID().getAccountID()).getId();
        for (TokenTransferList tokenTransferList : recordItem.getRecord().getTokenTransferListsList()) {
            for (NftTransfer nftTransfer : tokenTransferList.getNftTransfersList()) {
                if (nftTransfer.getSerialNumber() == NftTransferId.WILDCARD_SERIAL_NUMBER) {
                    EntityId newTreasury = EntityId.of(nftTransfer.getReceiverAccountID());
                    EntityId previousTreasury = EntityId.of(nftTransfer.getSenderAccountID());
                    EntityId tokenId = EntityId.of(tokenTransferList.getToken());

                    nftRepository.updateTreasury(tokenId.getId(), previousTreasury.getId(), newTreasury.getId(),
                            recordItem.getConsensusTimestamp(), payerAccountId);
                }
            }
        }
    }
}
