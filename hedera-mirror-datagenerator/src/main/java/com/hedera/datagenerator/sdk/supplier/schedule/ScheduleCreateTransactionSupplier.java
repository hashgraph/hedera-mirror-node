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

import javax.validation.constraints.Min;
import lombok.Data;
import lombok.Getter;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;

@Data
public class ScheduleCreateTransactionSupplier implements TransactionSupplier<ScheduleCreateTransaction> {

    private String adminKey;

    @Getter(lazy = true)
    private final Key adminPublicKey = PublicKey.fromString(adminKey);

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Getter(lazy = true)
    private final String memo = Utility.getMemo("Mirror node created test schedule");

    private String payerAccount;

    @Getter(lazy = true)
    private final AccountId payerAccountId = AccountId.fromString(payerAccount);

    @Override
    public ScheduleCreateTransaction get() {
        ScheduleCreateTransaction scheduleCreateTransaction = new ScheduleCreateTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setScheduleMemo(getMemo())
                .setTransactionMemo(getMemo());

        if (adminKey != null) {
            scheduleCreateTransaction.setAdminKey(getAdminPublicKey());
        }

        if (payerAccount != null) {
            scheduleCreateTransaction.setPayerAccountId(getPayerAccountId());
        }

        return scheduleCreateTransaction;
    }
}
