package com.hedera.datagenerator.sdk.supplier.account;

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
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.AbstractSchedulableTransactionSupplier;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.Transaction;

@Data
@Log4j2
public class AccountCreateTransactionSupplier extends AbstractSchedulableTransactionSupplier<AccountCreateTransaction> {

    @Min(1)
    private long initialBalance = 10_000_000;

    @Getter(lazy = true)
    private final String memo = Utility.getMemo("Mirror node created test account");

    private boolean receiverSignatureRequired = false;

    protected boolean scheduled = false;

    @Override
    public Transaction get() {
        AccountCreateTransaction accountCreateTransaction = new AccountCreateTransaction()
                .setAccountMemo(getMemo())
                .setInitialBalance(Hbar.fromTinybars(initialBalance))
                .setKey(getPublicKeys())
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setReceiverSignatureRequired(receiverSignatureRequired)
                .setTransactionMemo(getMemo());

        return isScheduled() ? getScheduleTransaction(accountCreateTransaction) : accountCreateTransaction;
    }
}
