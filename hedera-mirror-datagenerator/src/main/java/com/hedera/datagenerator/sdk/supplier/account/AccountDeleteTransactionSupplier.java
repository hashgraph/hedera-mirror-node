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

import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.account.AccountId;

@Data
public class AccountDeleteTransactionSupplier implements TransactionSupplier<AccountDeleteTransaction> {

    private static final List<String> requiredFields = Arrays.asList("accountId");

    //Required
    @NotBlank
    private String accountId;

    //Optional
    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String transferAccountId = "0.0.2";

    @Override
    public AccountDeleteTransaction get() {

        if (StringUtils.isBlank(accountId)) {
            throw new TransactionSupplierException(this, requiredFields);
        }

        return new AccountDeleteTransaction()
                .setDeleteAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo(Utility.getMemo("Mirror node deleted test account"))
                .setTransferAccountId(AccountId.fromString(transferAccountId));
    }
}
