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

import com.hedera.hapi.block.stream.output.protoc.StateChange;
import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

import java.util.Objects;

@Named
public class FileCreateTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {
        for (StateChanges stateChange : blockItem.stateChanges()) {
            for (StateChange change : stateChange.getStateChangesList()) {
                if (Objects.nonNull(change)
                        && change.hasMapUpdate()
                        && change.getMapUpdate().hasKey()
                        && change.getMapUpdate().getKey().hasFileIdKey()) {

                    var fileIdKey = change.getMapUpdate().getKey().getFileIdKey();
                    transactionRecordBuilder.getReceiptBuilder().setFileID(fileIdKey);
                }
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.FILECREATE;
    }
}
