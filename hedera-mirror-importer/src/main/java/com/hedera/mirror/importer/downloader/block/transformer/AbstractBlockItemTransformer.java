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

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.importer.downloader.block.BlockItemTransformer;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;

public abstract class AbstractBlockItemTransformer implements BlockItemTransformer {

    public TransactionRecord getTransactionRecord(
            BlockItem blockItem, ByteString transactionHash, TransactionBody transactionBody) {
        var transactionResult = blockItem.transactionResult();
        var receiptBuilder = TransactionReceipt.newBuilder().setStatus(transactionResult.getStatus());
        var transactionRecordBuilder = TransactionRecord.newBuilder()
                .addAllAutomaticTokenAssociations(transactionResult.getAutomaticTokenAssociationsList())
                .addAllPaidStakingRewards(transactionResult.getPaidStakingRewardsList())
                .addAllTokenTransferLists(transactionResult.getTokenTransferListsList())
                .setConsensusTimestamp(transactionResult.getConsensusTimestamp())
                .setParentConsensusTimestamp(transactionResult.getParentConsensusTimestamp())
                .setMemo(transactionBody.getMemo())
                .setScheduleRef(transactionResult.getScheduleRef())
                .setTransactionFee(transactionResult.getTransactionFeeCharged())
                .setTransactionID(transactionBody.getTransactionID())
                .setTransactionHash(transactionHash)
                .setTransferList(transactionResult.getTransferList())
                .setReceipt(receiptBuilder);

        updateTransactionRecord(blockItem, transactionRecordBuilder);
        return transactionRecordBuilder.build();
    }

    protected abstract void updateTransactionRecord(
            BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder);
}
