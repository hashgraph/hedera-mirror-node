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

import static com.hedera.hapi.block.stream.output.protoc.StateIdentifier.STATE_ID_PENDING_AIRDROPS;

import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.PendingAirdropId.TokenReferenceCase;
import com.hederahashgraph.api.proto.java.PendingAirdropRecord;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class TokenAirdropTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {
        if (!blockItem.successful()) {
            return;
        }

        for (var stateChanges : blockItem.stateChanges()) {
            for (var stateChange : stateChanges.getStateChangesList()) {
                if (stateChange.getStateId() == STATE_ID_PENDING_AIRDROPS.getNumber() && stateChange.hasMapUpdate()) {
                    var mapUpdate = stateChange.getMapUpdate();
                    var key = mapUpdate.getKey();
                    if (key.hasPendingAirdropIdKey()) {
                        var pendingId = key.getPendingAirdropIdKey();
                        var pendingAirdrop = PendingAirdropRecord.newBuilder().setPendingAirdropId(pendingId);
                        if (pendingId.getTokenReferenceCase() == TokenReferenceCase.FUNGIBLE_TOKEN_TYPE) {
                            var value = mapUpdate.getValue();
                            if (value.hasAccountPendingAirdropValue()) {
                                var accountValue = value.getAccountPendingAirdropValue();
                                if (accountValue.hasPendingAirdropValue()) {
                                    pendingAirdrop.setPendingAirdropValue(accountValue.getPendingAirdropValue());
                                }
                            }
                        }

                        transactionRecordBuilder.addNewPendingAirdrops(pendingAirdrop);
                    }
                }
            }
        }

        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasTokenAirdrop()) {
                var output = transactionOutput.getTokenAirdrop();
                var assessedCustomFees = output.getAssessedCustomFeesList();
                transactionRecordBuilder.addAllAssessedCustomFees(assessedCustomFees);
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENAIRDROP;
    }
}
