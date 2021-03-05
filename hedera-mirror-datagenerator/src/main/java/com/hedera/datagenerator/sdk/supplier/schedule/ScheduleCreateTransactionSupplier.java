package com.hedera.datagenerator.sdk.supplier.schedule;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.Min;
import lombok.Data;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionType;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.Transaction;

@Data
public class ScheduleCreateTransactionSupplier implements TransactionSupplier<ScheduleCreateTransaction> {

    private String adminKey;

    private String payerAccountId;

    private List<String> signatoryKeys;

    private TransactionType scheduledTransactionType;

    private Map<String, String> scheduledTransactionProperties = new LinkedHashMap<>();

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public ScheduleCreateTransaction get() {
        // retrieve inner transaction from appropriate supplier
        Transaction scheduledTransaction = getScheduledTransaction();

        ScheduleCreateTransaction scheduleCreateTransaction = scheduledTransaction.schedule()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setMemo(Utility.getMemo("Mirror node created test schedule"))
                .setTransaction(scheduledTransaction);

        if (adminKey != null) {
            PublicKey key = PublicKey.fromString(adminKey);
            scheduleCreateTransaction.setAdminKey(key);
        }

        if (payerAccountId != null) {
            scheduleCreateTransaction.setPayerAccountId(AccountId.fromString(payerAccountId));
        }

        if (signatoryKeys != null) {
            signatoryKeys.forEach(k -> {
                PrivateKey key = PrivateKey.fromString(k);
                scheduleCreateTransaction.addScheduleSignature(
                        key.getPublicKey(),
                        key.signTransaction(scheduledTransaction));
            });
        }
        return scheduleCreateTransaction;
    }

    private Transaction getScheduledTransaction() {
        // retrieve appropriate supplier for inner transaction
        return new ObjectMapper()
                .convertValue(scheduledTransactionProperties, scheduledTransactionType.getSupplier())
                .get();
    }
}
