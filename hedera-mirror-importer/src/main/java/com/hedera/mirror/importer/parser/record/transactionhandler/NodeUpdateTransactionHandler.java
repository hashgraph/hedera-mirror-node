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
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.Node;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import jakarta.inject.Named;

@Named
class NodeUpdateTransactionHandler extends AbstractNodeTransactionHandler {

    public NodeUpdateTransactionHandler(EntityListener entityListener, EntityProperties entityProperties) {
        super(entityListener, entityProperties);
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getNodeUpdate().getAccountId());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.NODEUPDATE;
    }

    @Override
    public Node parseNode(RecordItem recordItem) {
        if (recordItem.isSuccessful()) {
            var nodeUpdate = recordItem.getTransactionBody().getNodeUpdate();
            long consensusTimestamp = recordItem.getConsensusTimestamp();
            var node = new Node();

            if (nodeUpdate.hasAdminKey()) {
                node.setAdminKey(nodeUpdate.getAdminKey().toByteArray());
            }

            // As a special case, nodes migrated state to mirror nodes via a NodeUpdate instead of a proper NodeCreate
            if (recordItem.getTransactionRecord().getTransactionID().getNonce() > 0) {
                node.setCreatedTimestamp(consensusTimestamp);
            }

            node.setDeleted(false);
            node.setNodeId(nodeUpdate.getNodeId());
            node.setTimestampRange(Range.atLeast(consensusTimestamp));

            return node;
        }

        return null;
    }
}
