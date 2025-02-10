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

import static com.hedera.mirror.importer.util.Utility.DEFAULT_RUNNING_HASH_VERSION;

import com.hedera.hapi.block.stream.output.protoc.StateIdentifier;
import com.hedera.mirror.common.domain.transaction.BlockItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import jakarta.inject.Named;

@Named
final class ConsensusSubmitMessageTransformer extends AbstractBlockItemTransformer {

    @Override
    protected void updateTransactionRecord(BlockItem blockItem, TransactionRecord.Builder transactionRecordBuilder) {

        if (!blockItem.successful()) {
            return;
        }

        for (var transactionOutput : blockItem.transactionOutput()) {
            if (transactionOutput.hasSubmitMessage()) {
                var submitMessageOutput = transactionOutput.getSubmitMessage();
                var assessedCustomFees = submitMessageOutput.getAssessedCustomFeesList();
                transactionRecordBuilder.addAllAssessedCustomFees(assessedCustomFees);
                break;
            }
        }

        for (var stateChange : blockItem.stateChanges()) {
            for (var change : stateChange.getStateChangesList()) {
                if (change.getStateId() == StateIdentifier.STATE_ID_TOPICS.getNumber() && change.hasMapUpdate()) {
                    var value = change.getMapUpdate().getValue();
                    if (value.hasTopicValue()) {
                        var topicValue = value.getTopicValue();
                        transactionRecordBuilder
                                .getReceiptBuilder()
                                .setTopicRunningHash(topicValue.getRunningHash())
                                .setTopicSequenceNumber(topicValue.getSequenceNumber())
                                .setTopicRunningHashVersion(DEFAULT_RUNNING_HASH_VERSION);

                        return;
                    }
                }
            }
        }
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CONSENSUSSUBMITMESSAGE;
    }
}
