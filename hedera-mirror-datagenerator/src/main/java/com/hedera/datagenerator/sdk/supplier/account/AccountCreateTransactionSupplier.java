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
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;

@Data
@Log4j2
public class AccountCreateTransactionSupplier implements TransactionSupplier<AccountCreateTransaction> {

    @Min(1)
    private long initialBalance = 10_000_000;

    private boolean logKeys = false;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    private String publicKey;

    @Override
    public AccountCreateTransaction get() {
        return new AccountCreateTransaction()
                .setInitialBalance(Hbar.fromTinybars(initialBalance))
                .setKey(publicKey != null ? PublicKey.fromString(publicKey) : generateKeys())
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTransactionMemo(Utility.getMemo("Mirror node created test account"));
    }

    private PublicKey generateKeys() {
        PrivateKey privateKey = PrivateKey.generate();

        // Since these keys will never be seen again, if we want to reuse this account
        // provide an option to print them
        if (logKeys) {
            log.info("privateKey: {}", privateKey);
            log.info("publicKey: {}", privateKey.getPublicKey());
        }

        return privateKey.getPublicKey();
    }
}
