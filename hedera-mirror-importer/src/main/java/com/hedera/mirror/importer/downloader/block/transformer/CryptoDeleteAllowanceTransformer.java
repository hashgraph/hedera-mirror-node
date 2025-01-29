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
public class CryptoDeleteAllowanceTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {
        var transactionBody = blockItem.transaction().getBody();
        var cryptoDeleteAllowance = transactionBody.getCryptoDeleteAllowance();
        for (var nftAllowance : cryptoDeleteAllowance.getNftAllowancesList()) {
            for (var serialNumber : nftAllowance.getSerialNumbersList()) {
                var ownerId = nftAllowance.getOwner();
                var tokenId = nftAllowance.getTokenId();
                transactionRecordBuilder.addTokenTransferLists(TokenTransferList.newBuilder()
                        .setToken(tokenId)
                        .addNftTransfers(NftTransfer.newBuilder()
                                .setSenderAccountID(ownerId)
                                .setReceiverAccountID(AccountID.getDefaultInstance())
                                .build())
                        .build());
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTODELETEALLOWANCE;
    }
}
