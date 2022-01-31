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

import com.google.common.collect.Range;
import java.util.List;
import lombok.RequiredArgsConstructor;

import com.hedera.mirror.common.domain.entity.CryptoAllowance;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.common.domain.entity.TokenAllowance;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@RequiredArgsConstructor
abstract class AbstractAllowanceTransactionHandler implements TransactionHandler {

    private final EntityListener entityListener;

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return null;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!recordItem.isSuccessful()) {
            return;
        }

        for (var cryptoApproval : getCryptoAllowances(recordItem)) {
            CryptoAllowance cryptoAllowance = new CryptoAllowance();
            cryptoAllowance.setAmount(cryptoApproval.getAmount());
            cryptoAllowance.setPayerAccountId(recordItem.getPayerAccountId().getId());
            cryptoAllowance.setSpender(EntityId.of(cryptoApproval.getSpender()).getId());
            cryptoAllowance.setTimestampRange(Range.atLeast(transaction.getConsensusTimestamp()));
            entityListener.onCryptoAllowance(cryptoAllowance);
        }

        for (var nftApproval : getNftAllowances(recordItem)) {
            var approvedForAll = nftApproval.hasApprovedForAll() && nftApproval.getApprovedForAll().getValue();
            NftAllowance nftAllowance = new NftAllowance();
            nftAllowance.setApprovedForAll(approvedForAll);
            nftAllowance.setPayerAccountId(recordItem.getPayerAccountId().getId());
            nftAllowance.setSerialNumbers(nftApproval.getSerialNumbersList());
            nftAllowance.setSpender(EntityId.of(nftApproval.getSpender()).getId());
            nftAllowance.setTokenId(EntityId.of(nftApproval.getTokenId()).getId());
            nftAllowance.setTimestampLower(transaction.getConsensusTimestamp());
            entityListener.onNftAllowance(nftAllowance);
        }

        for (var tokenApproval : getTokenAllowances(recordItem)) {
            TokenAllowance tokenAllowance = new TokenAllowance();
            tokenAllowance.setAmount(tokenApproval.getAmount());
            tokenAllowance.setPayerAccountId(recordItem.getPayerAccountId().getId());
            tokenAllowance.setSpender(EntityId.of(tokenApproval.getSpender()).getId());
            tokenAllowance.setTokenId(EntityId.of(tokenApproval.getTokenId()).getId());
            tokenAllowance.setTimestampLower(transaction.getConsensusTimestamp());
            entityListener.onTokenAllowance(tokenAllowance);
        }
    }

    protected abstract List<com.hederahashgraph.api.proto.java.CryptoAllowance> getCryptoAllowances(RecordItem recordItem);

    protected abstract List<com.hederahashgraph.api.proto.java.NftAllowance> getNftAllowances(RecordItem recordItem);

    protected abstract List<com.hederahashgraph.api.proto.java.TokenAllowance> getTokenAllowances(RecordItem recordItem);
}
