/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.publish.transaction.schedule;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ScheduleCreateTransaction;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.mirror.monitor.publish.transaction.AdminKeyable;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.util.Utility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Data
@Log4j2
public class ScheduleCreateTransactionSupplier implements TransactionSupplier<ScheduleCreateTransaction>, AdminKeyable {

    private String adminKey;

    @Getter(lazy = true)
    private final Key adminPublicKey = adminKey != null ? PublicKey.fromString(adminKey) : null;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String operatorAccountId;

    @Getter(lazy = true)
    private final AccountId operatorId = AccountId.fromString(operatorAccountId);

    @NotBlank
    private String payerAccount;

    @Getter(lazy = true)
    private final AccountId payerAccountId = AccountId.fromString(payerAccount);

    @Override
    public ScheduleCreateTransaction get() {
        Hbar maxHbarTransactionFee = Hbar.fromTinybars(getMaxTransactionFee());
        TransferTransaction innerTransaction = new TransferTransaction()
                .setMaxTransactionFee(maxHbarTransactionFee)
                .addHbarTransfer(getOperatorId(), Hbar.fromTinybars(1L).negated())
                .addHbarTransfer(getPayerAccountId(), Hbar.fromTinybars(1L));

        return new ScheduleCreateTransaction()
                .setAdminKey(getAdminPublicKey())
                .setMaxTransactionFee(maxHbarTransactionFee)
                .setPayerAccountId(getPayerAccountId())
                .setScheduleMemo(Utility.getMemo("Mirror node created test schedule"))
                .setScheduledTransaction(innerTransaction);
    }
}
