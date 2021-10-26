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

import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.Entity;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.NftTransferId;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.repository.NftRepository;
import com.hedera.mirror.importer.util.Utility;

@Named
class TokenUpdateTransactionHandler extends AbstractEntityCrudTransactionHandler<Entity> {

    private final NftRepository nftRepository;

    TokenUpdateTransactionHandler(EntityListener entityListener, NftRepository nftRepository) {
        super(entityListener, TransactionTypeEnum.TOKENUPDATE);
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
            EntityId autoRenewAccountId = EntityId.of(transactionBody.getAutoRenewAccount());
            entity.setAutoRenewAccountId(autoRenewAccountId);
            entityListener.onEntityId(autoRenewAccountId);
        }

        if (transactionBody.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(transactionBody.getAutoRenewPeriod().getSeconds());
        }

        if (transactionBody.hasExpiry()) {
            entity.setExpirationTimestamp(Utility.timestampInNanosMax(transactionBody.getExpiry()));
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
