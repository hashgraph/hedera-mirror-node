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

import static com.hedera.mirror.common.util.DomainUtils.toBytes;
import static com.hedera.mirror.importer.util.Utility.RECOVERABLE_ERROR;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.Token;
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
class TokenMintTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenMint().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENMINT;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isTokens() || !recordItem.isSuccessful()) {
            return;
        }

        var transactionBody = recordItem.getTransactionBody().getTokenMint();
        var tokenId = transaction.getEntityId();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        long newTotalSupply = recordItem.getTransactionRecord().getReceipt().getNewTotalSupply();

        var token = new Token();
        token.setTimestampLower(consensusTimestamp);
        token.setTokenId(tokenId.getId());
        token.setTotalSupply(newTotalSupply);
        entityListener.onToken(token);

        var serialNumbers = recordItem.getTransactionRecord().getReceipt().getSerialNumbersList();
        for (int i = 0; i < serialNumbers.size(); i++) {
            if (i >= transactionBody.getMetadataCount()) {
                log.warn(
                        RECOVERABLE_ERROR + "Mismatch between {} metadata and {} serial numbers at {}",
                        transactionBody.getMetadataCount(),
                        serialNumbers,
                        consensusTimestamp);
                break;
            }

            var nft = Nft.builder()
                    .createdTimestamp(consensusTimestamp)
                    .deleted(false)
                    .metadata(toBytes(transactionBody.getMetadata(i)))
                    .serialNumber(serialNumbers.get(i))
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .tokenId(tokenId.getId())
                    .build();
            entityListener.onNft(nft);
        }
    }
}
