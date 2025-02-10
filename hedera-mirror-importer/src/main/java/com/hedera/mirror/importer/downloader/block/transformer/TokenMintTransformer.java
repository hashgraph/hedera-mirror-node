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

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_NFTS;
import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_TOKENS;

import com.hedera.hapi.block.stream.output.protoc.MapUpdateChange;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class TokenMintTransformer extends AbstractBlockItemTransformer {

    @SuppressWarnings("java:S3776")
    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        var receiptBuilder = transactionRecordBuilder.getReceiptBuilder();
        for (var stateChanges : blockItem.stateChanges()) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (stateChange.hasMapUpdate()) {
                    var mapUpdate = stateChange.getMapUpdate();
                    if (stateChange.getStateId() == STATE_ID_TOKENS.getNumber()) {
                        if (setSupply(mapUpdate, receiptBuilder)) {
                            return;
                        }
                    } else if (stateChange.getStateId() == STATE_ID_NFTS.getNumber()) {
                        var key = mapUpdate.getKey();
                        if (key.hasNftIdKey()) {
                            receiptBuilder.addSerialNumbers(key.getNftIdKey().getSerialNumber());
                            setSupply(mapUpdate, receiptBuilder);
                        }
                    }
                }
            }
        }
    }

    private boolean setSupply(MapUpdateChange mapUpdate, TransactionReceipt.Builder receiptBuilder) {
        if (mapUpdate.hasValue()) {
            var value = mapUpdate.getValue();
            if (value.hasTokenValue()) {
                receiptBuilder.setNewTotalSupply(value.getTokenValue().getTotalSupply());
                return true;
            }
        }

        return false;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENMINT;
    }
}
