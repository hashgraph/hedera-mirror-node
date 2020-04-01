package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.domain.RecordItem;

/**
 * TransactionHandler interface abstracts the logic for processing different kinds for transactions. For each
 * transaction type, there exists an unique implementation of TransactionHandler which encapsulates all logic specific
 * to processing of that transaction type. A single {@link com.hederahashgraph.api.proto.java.Transaction} and its
 * associated info (TransactionRecord, deserialized TransactionBody, etc) are all encapsulated together in a single
 * {@link RecordItem}. Hence, most functions of this interface require RecordItem as a parameter.
 */
public interface TransactionHandler {
    /**
     * @return main entity associated with this transaction
     */
    EntityId getEntityId(RecordItem recordItem);

    /**
     * Override this function if a transaction needs to update proxyAccountId in {@link
     * com.hedera.mirror.importer.domain.Entities}. If value returned is null, or if {@link EntityId#getEntityNum()} is
     * 0 for the returned value, then proxyAccountId is not updated.
     */
    default EntityId getProxyAccountId(RecordItem recordItem) {
        return null;
    }

    /**
     * Override to return true if an implementation wants to be able to update the entity returned by
     * {@link #getEntityId(RecordItem)}.
     */
    default boolean updatesEntity() {
        return false;
    }
}
