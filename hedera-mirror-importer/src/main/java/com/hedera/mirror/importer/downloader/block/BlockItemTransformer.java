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

package com.hedera.mirror.importer.downloader.block;

import com.google.protobuf.ByteString;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;

public abstract class BlockItemTransformer {

    public TransactionRecord getTransactionRecord(BlockItem blockItem, TransactionBody transactionBody) {
        var transactionResult = blockItem.transactionResult();
        var receiptBuilder = TransactionReceipt.newBuilder().setStatus(transactionResult.getStatus());
        var transactionRecordBuilder = TransactionRecord.newBuilder()
                .addAllAutomaticTokenAssociations(transactionResult.getAutomaticTokenAssociationsList())
                .addAllTokenTransferLists(transactionResult.getTokenTransferListsList())
                .setConsensusTimestamp(transactionResult.getConsensusTimestamp())
                .setMemoBytes(ByteString.copyFromUtf8(transactionBody.getMemo()))
                .setTransactionFee(transactionResult.getTransactionFeeCharged())
                .setTransactionID(transactionBody.getTransactionID())
                .setTransferList(transactionResult.getTransferList())
                // Note on TransactionHash:
                // There is an Open Issue in HIP 1056 whether the transaction hash will be included in the block stream
                // or if it will need to be calculated by block stream consumers.
                .setReceipt(receiptBuilder);

        updateTransactionRecord(blockItem.transactionOutput(), transactionRecordBuilder);
        return transactionRecordBuilder.build();
    }

    protected abstract void updateTransactionRecord(
            List<TransactionOutput> transactionOutputs, TransactionRecord.Builder transactionRecordBuilder);
}
