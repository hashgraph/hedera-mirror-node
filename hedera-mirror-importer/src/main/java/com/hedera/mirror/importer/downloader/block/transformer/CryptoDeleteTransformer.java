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
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class CryptoDeleteTransformer extends AbstractBlockItemTransformer {
    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {
        var transactionBody = blockItem.transaction().getBody().getCryptoDelete();
        var transferAccountId = transactionBody.getTransferAccountID();
        var deleteAccountId = transactionBody.getDeleteAccountID();
        var receiptBuilder = transactionRecordBuilder.getReceiptBuilder();

        receiptBuilder.setAccountID(deleteAccountId);
        if (!transferAccountId.equals(AccountID.getDefaultInstance())) {
            transactionRecordBuilder
                    .getTransferListBuilder()
                    .mergeFrom(AccountAmount.newBuilder()
                            .setAccountID(deleteAccountId)
                            .setAmount(-1)
                            .build());
            transactionRecordBuilder
                    .getTransferListBuilder()
                    .mergeFrom(AccountAmount.newBuilder()
                            .setAccountID(transferAccountId)
                            .setAmount(+1)
                            .build());
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTODELETE;
    }
}
