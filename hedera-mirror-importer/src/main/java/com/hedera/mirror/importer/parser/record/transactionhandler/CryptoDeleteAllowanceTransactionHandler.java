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

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.contractlog.ApproveAllowanceIndexedContractLog;
import com.hedera.mirror.importer.parser.contractlog.SyntheticContractLogService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class CryptoDeleteAllowanceTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final SyntheticContractLogService syntheticContractLogService;

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTODELETEALLOWANCE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        long consensusTimestamp = recordItem.getConsensusTimestamp();
        for (var nftAllowance :
                recordItem.getTransactionBody().getCryptoDeleteAllowance().getNftAllowancesList()) {
            var ownerId = EntityId.of(nftAllowance.getOwner());
            var tokenId = EntityId.of(nftAllowance.getTokenId());

            ownerId = EntityId.isEmpty(ownerId) ? recordItem.getPayerAccountId() : ownerId;
            for (var serialNumber : nftAllowance.getSerialNumbersList()) {
                var nft = Nft.builder()
                        .serialNumber(serialNumber)
                        .timestampRange(Range.atLeast(consensusTimestamp))
                        .tokenId(tokenId.getId())
                        .build();
                entityListener.onNft(nft);
                syntheticContractLogService.create(new ApproveAllowanceIndexedContractLog(
                        recordItem, tokenId, ownerId, EntityId.EMPTY, serialNumber));
            }

            recordItem.addEntityId(ownerId);
            recordItem.addEntityId(tokenId);
        }
    }
}
