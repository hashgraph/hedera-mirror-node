/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
abstract class AbstractTransactionHandler implements TransactionHandler {

    @Override
    public final void updateTransaction(Transaction transaction, RecordItem recordItem) {
        addCommonEntityIds(transaction, recordItem);
        doUpdateTransaction(transaction, recordItem);
        updateEntity(transaction, recordItem);
    }

    protected void addCommonEntityIds(Transaction transaction, RecordItem recordItem) {
        recordItem.addEntityId(transaction.getEntityId());
        recordItem.addEntityId(transaction.getNodeAccountId());
        recordItem.addEntityId(transaction.getPayerAccountId());
    }

    protected void doUpdateTransaction(Transaction transaction, RecordItem recordItem) {}

    protected void updateEntity(Transaction transaction, RecordItem recordItem) {}
}
