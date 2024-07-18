/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import jakarta.inject.Named;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;

@CustomLog
@Named
@RequiredArgsConstructor
class NodeDeleteTransactionHandler extends AbstractTransactionHandler {

    private final EntityListener entityListener;

    @Override
    public TransactionType getType() {
        return TransactionType.NODEDELETE;
    }

    @Override
    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {

        transaction.setTransactionBytes(recordItem.getTransaction().toByteArray());
        transaction.setTransactionRecordBytes(recordItem.getTransactionRecord().toByteArray());
        parseNode(recordItem);
    }

    private void parseNode(RecordItem recordItem) {
        var nodeCreate = recordItem.getTransactionBody().getNodeUpdate();
        long consensusTimestamp = recordItem.getConsensusTimestamp();
        var node = new Node();
        node.setAdminKey(String.valueOf(nodeCreate.getAdminKey()));
        node.setCreatedTimestamp(consensusTimestamp);
        node.setDeleted(true);
        node.setTimestampLower(consensusTimestamp);
        node.setNodeId(recordItem.getTransactionRecord().getReceipt().getNodeId());
        entityListener.onNode(node);
    }
}
