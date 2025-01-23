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
import com.hedera.mirror.common.domain.token.TokenAccount;
import com.hedera.mirror.common.domain.token.TokenAirdrop;
import com.hedera.mirror.common.domain.token.TokenAirdropStateEnum;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hedera.mirror.importer.util.Utility;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
abstract class AbstractTokenUpdateAirdropTransactionHandler extends AbstractTransactionHandler {

    private final EntityIdService entityIdService;
    private final EntityListener entityListener;
    private final EntityProperties entityProperties;
    private final Function<RecordItem, List<PendingAirdropId>> extractor;
    private final TokenAirdropStateEnum state;
    private final TransactionType type;

    @Override
    public TransactionType getType() {
        return type;
    }

    @Override
    public void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokenAirdrops() || !recordItem.isSuccessful()) {
            return;
        }

        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var pendingAirdropIds = extractor.apply(recordItem);
        for (var pendingAirdropId : pendingAirdropIds) {
            var receiver =
                    entityIdService.lookup(pendingAirdropId.getReceiverId()).orElse(EntityId.EMPTY);
            var sender = entityIdService.lookup(pendingAirdropId.getSenderId()).orElse(EntityId.EMPTY);
            if (EntityId.isEmpty(receiver) || EntityId.isEmpty(sender)) {
                Utility.handleRecoverableError("Invalid update token airdrop entity id at {}", consensusTimestamp);
                continue;
            }

            recordItem.addEntityId(receiver);
            recordItem.addEntityId(sender);

            var tokenAirdrop = new TokenAirdrop();
            tokenAirdrop.setState(state);
            tokenAirdrop.setReceiverAccountId(receiver.getId());
            tokenAirdrop.setSenderAccountId(sender.getId());
            tokenAirdrop.setTimestampRange(Range.atLeast(consensusTimestamp));

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

            if (state == TokenAirdropStateEnum.CLAIMED) {
                associateTokenAccount(tokenEntityId, receiver, consensusTimestamp);
            }

            entityListener.onTokenAirdrop(tokenAirdrop);
        }
    }

    private void associateTokenAccount(EntityId token, EntityId receiver, long consensusTimestamp) {
        var tokenAccount = new TokenAccount();
        tokenAccount.setAccountId(receiver.getId());
        tokenAccount.setAssociated(true);
        tokenAccount.setAutomaticAssociation(false);
        tokenAccount.setBalance(0L);
        tokenAccount.setBalanceTimestamp(consensusTimestamp);
        tokenAccount.setClaim(true);
        tokenAccount.setCreatedTimestamp(consensusTimestamp);
        tokenAccount.setTimestampLower(consensusTimestamp);
        tokenAccount.setTokenId(token.getId());
        entityListener.onTokenAccount(tokenAccount);
    }
}
