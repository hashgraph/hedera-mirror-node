/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.downloader.block.transformer;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.*;
import jakarta.inject.Named;

@Named
final class CryptoApproveAllowanceTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {
        var transactionBody = blockItem.transaction().getBody();
        var cryptoApproveAllowance = transactionBody.getCryptoApproveAllowance();
        var cryptoAllowances = cryptoApproveAllowance.getCryptoAllowancesList();
        var cryptoIterator = cryptoAllowances.listIterator(cryptoAllowances.size());
        while (cryptoIterator.hasPrevious()) {
            var cryptoAllowance = cryptoIterator.previous();
            var ownerId = cryptoAllowance.getOwner();
            var spenderId = cryptoAllowance.getSpender();
            transactionRecordBuilder
                    .getTransferListBuilder()
                    .mergeFrom(CryptoAllowance.newBuilder()
                            .setOwner(ownerId)
                            .setSpender(spenderId)
                            .build());
        }

        var nftAllowances = cryptoApproveAllowance.getNftAllowancesList();
        var nftIterator = nftAllowances.listIterator(nftAllowances.size());
        while (nftIterator.hasPrevious()) {
            var nftAllowance = nftIterator.previous();
            for (var serialNumber : nftAllowance.getSerialNumbersList()) {
                var ownerId = nftAllowance.getOwner();
                var tokenId = nftAllowance.getTokenId();
                var spenderId = nftAllowance.getSpender();
                var delegating_spender = nftAllowance.getDelegatingSpender();
                transactionRecordBuilder.addTokenTransferLists(TokenTransferList.newBuilder()
                        .addNftTransfers(NftTransfer.newBuilder()
                                .mergeFrom(NftAllowance.newBuilder()
                                        .setTokenId(tokenId)
                                        .setOwner(ownerId)
                                        .setSpender(spenderId)
                                        .setApprovedForAll(nftAllowance.getApprovedForAll())
                                        .setDelegatingSpender(delegating_spender)
                                        .build())
                                .build())
                        .build());
            }
        }

        var tokenAllowances = cryptoApproveAllowance.getTokenAllowancesList();
        var tokenIterator = tokenAllowances.listIterator(tokenAllowances.size());

        while (tokenIterator.hasPrevious()) {
            var tokenAllowance = tokenIterator.previous();
            var ownerId = tokenAllowance.getOwner();
            var spenderId = tokenAllowance.getSpender();
            var tokenId = tokenAllowance.getTokenId();

            transactionRecordBuilder.addTokenTransferLists(TokenTransferList.newBuilder()
                    .mergeFrom(TokenAllowance.newBuilder()
                            .setTokenId(tokenId)
                            .setOwner(ownerId)
                            .setSpender(spenderId)
                            .build())
                    .build());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOAPPROVEALLOWANCE;
    }
}
