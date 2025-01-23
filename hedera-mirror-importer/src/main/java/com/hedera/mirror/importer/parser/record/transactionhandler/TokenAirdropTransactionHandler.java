/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.domain.token.TokenAirdropStateEnum;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hederahashgraph.api.proto.java.TokenID;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class TokenAirdropTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokenAirdrops() || !recordItem.isSuccessful()) {
            return;
        }

        var pendingAirdrops = recordItem.getTransactionRecord().getNewPendingAirdropsList();
        for (var pendingAirdrop : pendingAirdrops) {
            var pendingAirdropId = pendingAirdrop.getPendingAirdropId();
            var receiver = EntityId.of(pendingAirdropId.getReceiverId());
            var sender = EntityId.of(pendingAirdropId.getSenderId());
            recordItem.addEntityId(receiver);
            recordItem.addEntityId(sender);

            var tokenAirdrop = new TokenAirdrop();
            tokenAirdrop.setState(TokenAirdropStateEnum.PENDING);
            tokenAirdrop.setReceiverAccountId(receiver.getId());
            tokenAirdrop.setSenderAccountId(sender.getId());
            tokenAirdrop.setTimestampRange(Range.atLeast(recordItem.getConsensusTimestamp()));

            TokenID tokenId;
            if (pendingAirdropId.hasFungibleTokenType()) {
                tokenId = pendingAirdropId.getFungibleTokenType();
                var amount = pendingAirdrop.getPendingAirdropValue().getAmount();
                tokenAirdrop.setAmount(amount);
            } else {
                tokenId = pendingAirdropId.getNonFungibleToken().getTokenID();
                var serialNumber = pendingAirdropId.getNonFungibleToken().getSerialNumber();
                tokenAirdrop.setSerialNumber(serialNumber);
            }

            var tokenEntityId = EntityId.of(tokenId);
            recordItem.addEntityId(tokenEntityId);
            tokenAirdrop.setTokenId(tokenEntityId.getId());
            entityListener.onTokenAirdrop(tokenAirdrop);
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENAIRDROP;
    }
}
