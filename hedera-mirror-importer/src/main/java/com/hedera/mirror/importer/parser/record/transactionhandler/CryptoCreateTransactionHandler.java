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

import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import javax.inject.Named;
import lombok.AllArgsConstructor;

import com.hedera.mirror.importer.domain.Entities;
import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.domain.Transaction;
import com.hedera.mirror.importer.parser.domain.RecordItem;
import com.hedera.mirror.importer.repository.EntityRepository;

@Named
@AllArgsConstructor
public class CryptoCreateTransactionHandler implements TransactionHandler {
    private final EntityRepository entityRepository;

    @Override
    public EntityId getEntityId(RecordItem recordItem) {
        return EntityId.of(recordItem.getRecord().getReceipt().getAccountID());
    }

    @Override
    public boolean updatesEntity() {
        return true;
    }

    @Override
    public void updateTransaction(Transaction transaction, RecordItem recordItem) {
        transaction.setInitialBalance(recordItem.getTransactionBody().getCryptoCreateAccount().getInitialBalance());
    }

    @Override
    public void updateEntity(Entities entity, RecordItem recordItem) {
        CryptoCreateTransactionBody txMessage = recordItem.getTransactionBody().getCryptoCreateAccount();
        if (txMessage.hasAutoRenewPeriod()) {
            entity.setAutoRenewPeriod(txMessage.getAutoRenewPeriod().getSeconds());
        }
        if (txMessage.hasKey()) {
            entity.setKey(txMessage.getKey().toByteArray());
        }
        // Stream contains transactions with proxyAccountID explicitly set to '0.0.0'. However it's not a valid entity,
        // so no need to persist it to repo.
        EntityId proxyAccountEntityId = EntityId.of(txMessage.getProxyAccountID());
        if (proxyAccountEntityId != null) {
            entity.setProxyAccountId(entityRepository.lookupOrCreateId(proxyAccountEntityId));
        }
    }
}
