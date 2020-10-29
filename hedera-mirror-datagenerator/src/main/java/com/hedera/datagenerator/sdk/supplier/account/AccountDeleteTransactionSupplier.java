package com.hedera.datagenerator.sdk.supplier.account;

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

import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.account.AccountId;

@Builder
@Value
@Log4j2
public class AccountDeleteTransactionSupplier implements TransactionSupplier<AccountDeleteTransaction> {

    //Required
    private final String accountId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Builder.Default
    private final String transferAccountId = "0.0.2";

    @Override
    public AccountDeleteTransaction get() {
        return new AccountDeleteTransaction()
                .setDeleteAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo(Utility.getEncodedTimestamp() + "_Mirror node deleted test account at " + Instant
                        .now())
                .setTransferAccountId(AccountId.fromString(transferAccountId));
    }
}
