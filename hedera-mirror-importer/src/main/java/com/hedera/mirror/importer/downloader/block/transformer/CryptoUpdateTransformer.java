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
import com.hederahashgraph.api.proto.java.TokenAssociation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
public class CryptoUpdateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {
        var transactionBody = blockItem.transaction().getBody().getCryptoUpdateAccount();
        var accountIdToUpdate = transactionBody.getAccountIDToUpdate();

        var receiptBuilder = transactionRecordBuilder.getReceiptBuilder();
        receiptBuilder.setAccountID(accountIdToUpdate);

        var transferList = transactionRecordBuilder.getTransferListBuilder();

        transferList.addAccountAmounts(AccountAmount.newBuilder()
                .setAccountID(accountIdToUpdate)
                .setAmount(0)
                .build());

        if (transactionBody.hasMaxAutomaticTokenAssociations()) {
            var tokenAssociation = TokenAssociation.newBuilder()
                    .setAccountId(accountIdToUpdate)
                    .setTokenId(TokenID.getDefaultInstance())
                    .build();
            transactionRecordBuilder.addAutomaticTokenAssociations(tokenAssociation);
        }

        if (transactionBody.hasDeclineReward()
                && transactionBody.getDeclineReward().getValue()) {
            var stakingReward = AccountAmount.newBuilder()
                    .setAccountID(accountIdToUpdate)
                    .setAmount(0)
                    .build();
            transactionRecordBuilder.addPaidStakingRewards(stakingReward);
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOUPDATEACCOUNT;
    }
}
