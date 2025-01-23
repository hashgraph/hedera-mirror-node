/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
class NodeDeleteTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;
    private final EntityProperties entityProperties;

    @Override
    public TransactionType getType() {
        return TransactionType.NODEDELETE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {
        if (!entityProperties.getPersist().isNodes()) {
            return;
        }
        transaction.setTransactionBytes(recordItem.getTransaction().toByteArray());
        transaction.setTransactionRecordBytes(recordItem.getTransactionRecord().toByteArray());
        parseNode(recordItem);
    }

    private void parseNode(RecordItem recordItem) {
        if (recordItem.isSuccessful()) {
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            var body = recordItem.getTransactionBody().getNodeDelete();
            entityListener.onNode(Node.builder()
                    .deleted(true)
                    .nodeId(body.getNodeId())
                    .timestampRange(Range.atLeast(consensusTimestamp))
                    .build());
        }
    }
}
