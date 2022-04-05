package com.hedera.mirror.importer.parser.record.transactionhandler;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import java.util.List;
import javax.inject.Named;

import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;

@Named
class CryptoApproveAllowanceTransactionHandler extends AbstractAllowanceTransactionHandler {

    public CryptoApproveAllowanceTransactionHandler(EntityListener entityListener) {
        super(entityListener);
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOAPPROVEALLOWANCE;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        if (recordItem.isSuccessful()) {
            super.updateTransaction(transaction, recordItem);
        }
    }

    @Override
    protected List<CryptoAllowance> getCryptoAllowances(RecordItem recordItem) {
        return getTransactionBody(recordItem).getCryptoAllowancesList();
    }

    @Override
    protected List<NftAllowance> getNftAllowances(RecordItem recordItem) {
        return getTransactionBody(recordItem).getNftAllowancesList();
    }

    @Override
    protected List<TokenAllowance> getTokenAllowances(RecordItem recordItem) {
        return getTransactionBody(recordItem).getTokenAllowancesList();
    }

    private CryptoApproveAllowanceTransactionBody getTransactionBody(RecordItem recordItem) {
        return recordItem.getTransactionBody().getCryptoApproveAllowance();
    }
}
