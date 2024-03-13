/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.util.DomainUtils.toBytes;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
class TokenUpdateNftsTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenUpdateNfts().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENUPDATENFTS;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenUpdateNfts();

        // Since there's only one updatable field, this is a no-op. In the future if there's multiple fields we'll have
        // to rework this logic
        if (!transactionBody.hasMetadata()) {
            return;
        }

        var tokenId = transaction.getEntityId();
        var consensusTimestamp = recordItem.getConsensusTimestamp();
        var nftBuilder = Nft.builder()
                .metadata(toBytes(transactionBody.getMetadata().getValue()))
                .timestampRange(Range.atLeast(consensusTimestamp))
                .tokenId(tokenId.getId())
                .tokenId(tokenId.getId());

        var serialNumbers = transactionBody.getSerialNumbersList();
        for (int i = 0; i < serialNumbers.size(); i++) {
            var nft = nftBuilder.serialNumber(serialNumbers.get(i)).build();
            entityListener.onNft(nft);
        }
    }
}
