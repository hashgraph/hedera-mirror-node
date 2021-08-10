package com.hedera.mirror.monitor.publish.transaction.account;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class AccountCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        AccountCreateTransaction actual = accountCreateTransactionSupplier.get();
        AccountCreateTransaction expected = new AccountCreateTransaction()
                .setAccountMemo(actual.getAccountMemo())
                .setInitialBalance(Hbar.fromTinybars(10_000_000))
                .setKey(actual.getKey())
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setReceiverSignatureRequired(false)
                .setTransactionMemo(actual.getTransactionMemo());
        assertAll(
                () -> assertThat(actual.getAccountMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual.getKey()).isNotNull(),
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        PublicKey key = PrivateKey.generate().getPublicKey();

        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        accountCreateTransactionSupplier.setInitialBalance(1);
        accountCreateTransactionSupplier.setMaxTransactionFee(1);
        accountCreateTransactionSupplier.setReceiverSignatureRequired(true);
        accountCreateTransactionSupplier.setPublicKey(key.toString());
        AccountCreateTransaction actual = accountCreateTransactionSupplier.get();

        AccountCreateTransaction expected = new AccountCreateTransaction()
                .setAccountMemo(actual.getAccountMemo())
                .setInitialBalance(ONE_TINYBAR)
                .setKey(key)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setReceiverSignatureRequired(true)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual.getAccountMemo()).contains("Mirror node created test account"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
