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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.domain.token.TokenAirdropStateEnum;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import jakarta.inject.Named;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class TokenUpdateAirdropTransactionHandler {

    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    protected void doUpdateTransaction(RecordItem recordItem, TokenAirdropStateEnum state) {
        if (!entityProperties.getPersist().isTokenAirdrops()
                || !entityProperties.getPersist().isTokens()
                || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody();
        if ((state == TokenAirdropStateEnum.CANCELLED && !transactionBody.hasTokenCancelAirdrop())
                || (state == TokenAirdropStateEnum.CLAIMED && !transactionBody.hasTokenClaimAirdrop())) {
            return;
        }

        var pendingAirdropIds = getPendingAirdropIds(transactionBody, state);
        for (var pendingAirdropId : pendingAirdropIds) {
            var receiver = EntityId.of(pendingAirdropId.getReceiverId());
            recordItem.addEntityId(receiver);
            var sender = EntityId.of(pendingAirdropId.getSenderId());
            recordItem.addEntityId(sender);

            var tokenAirdrop = new TokenAirdrop();
            tokenAirdrop.setState(state);
            tokenAirdrop.setReceiverAccountId(receiver.getId());
            tokenAirdrop.setSenderAccountId(sender.getId());
            tokenAirdrop.setTimestampRange(Range.atLeast(recordItem.getConsensusTimestamp()));

            TokenID tokenId;
            if (pendingAirdropId.hasFungibleTokenType()) {
                tokenId = pendingAirdropId.getFungibleTokenType();
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

    public EntityId getEntity(TransactionBody transactionBody, TokenAirdropStateEnum state) {
        var pendingAirdrops = getPendingAirdropIds(transactionBody, state);
        if (pendingAirdrops.size() > 0) {
            var pendingAirdropId = pendingAirdrops.getFirst();
            return entityIdService.lookup(pendingAirdropId.getReceiverId()).orElse(EntityId.EMPTY);
        }

        return null;
    }

    private List<PendingAirdropId> getPendingAirdropIds(TransactionBody transactionBody, TokenAirdropStateEnum state) {
        return state == TokenAirdropStateEnum.CANCELLED
                ? transactionBody.getTokenCancelAirdrop().getPendingAirdropsList()
                : transactionBody.getTokenClaimAirdrop().getPendingAirdropsList();
    }
}
