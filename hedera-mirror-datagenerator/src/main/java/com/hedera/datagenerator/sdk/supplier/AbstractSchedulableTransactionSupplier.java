package com.hedera.datagenerator.sdk.supplier;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.Min;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.common.Utility;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;

@Data
@Log4j2
@NoArgsConstructor
public abstract class AbstractSchedulableTransactionSupplier<T extends Transaction<T>> implements TransactionSupplier<T> {

    private final boolean logKeys = false;

    private final Integer signatoryCount = null;

    @Getter(lazy = true)
    private final List<PrivateKey> privateKeyList = getSigningKeys();

    @Getter(lazy = true)
    private final List<PrivateKey> fullSignatoryList = createSignatoryKeys();

    @Min(1)
    private final Integer totalSignatoryCount = 1;

    @Getter(lazy = true)
    private final String scheduleMemo = Utility.getMemo("Mirror node created test schedule create");

    @Min(1)
    protected final long maxTransactionFee = 1_000_000_000;

    private String operatorAccountId;

    private String nodeAccountId;

    @Getter(lazy = true)
    private final AccountId operatorId = AccountId.fromString(operatorAccountId);

    @Getter(lazy = true)
    private final AccountId nodeId = AccountId.fromString(nodeAccountId);

    protected ScheduleCreateTransaction getScheduleTransaction(Transaction scheduledTransaction) {
        TransactionId transactionId = TransactionId.generate(getOperatorId()).setScheduled(true);

        // set nodeAccountId and freeze inner transaction
        scheduledTransaction.setNodeAccountIds(Collections.singletonList(getNodeId()));
        scheduledTransaction.setTransactionId(transactionId);
        scheduledTransaction.freeze();

        ScheduleCreateTransaction scheduleCreateTransaction = scheduledTransaction.schedule()
                .setMaxTransactionFee(Hbar.fromTinybars(getMaxTransactionFee()))
                .setScheduleMemo(getScheduleMemo())
                .setTransactionId(transactionId.setScheduled(false))
                .setTransactionMemo(getScheduleMemo());

        // add initial set of required signatures to ScheduleCreate transaction
        if (totalSignatoryCount > 0) {
            getPrivateKeyList().forEach(k -> scheduleCreateTransaction.addScheduleSignature(
                    k.getPublicKey(),
                    k.signTransaction(scheduledTransaction)));
        }

        return scheduleCreateTransaction;
    }

    protected KeyList getPublicKeys() {
        KeyList keys = new KeyList();
        getFullSignatoryList().forEach(key -> {
            keys.add(key.getPublicKey());
        });

        return keys;
    }

    private int getNumberOfSignatories() {
        return signatoryCount == null ? totalSignatoryCount : signatoryCount;
    }

    private List<PrivateKey> getSigningKeys() {
        return getFullSignatoryList().subList(0, getNumberOfSignatories());
    }

    private List<PrivateKey> createSignatoryKeys() {
        List<PrivateKey> keys = new ArrayList<>();
        for (int i = 0; i < totalSignatoryCount; i++) {
            PrivateKey privateKey = PrivateKey.generate();
            if (logKeys) {
                log.info("privateKey {}: {}", i, privateKey);
                log.info("publicKey {}: {}", i, privateKey.getPublicKey());
            }
            keys.add(privateKey);
        }

        return keys;
    }
}
