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

package com.hedera.mirror.monitor.publish.transaction.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class AccountCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        AccountCreateTransaction actual = accountCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(Hbar.fromTinybars(10_000_000), AccountCreateTransaction::getInitialBalance)
                .returns(MAX_TRANSACTION_FEE_HBAR, AccountCreateTransaction::getMaxTransactionFee)
                .returns(false, AccountCreateTransaction::getReceiverSignatureRequired)
                .satisfies(a -> assertThat(a.getKey()).isNotNull())
                .extracting(AccountCreateTransaction::getAccountMemo, STRING)
                .contains("Mirror node created test account");
    }

    @Test
    void createWithCustomData() {
        PublicKey key = PrivateKey.generateED25519().getPublicKey();

        AccountCreateTransactionSupplier accountCreateTransactionSupplier = new AccountCreateTransactionSupplier();
        accountCreateTransactionSupplier.setInitialBalance(1);
        accountCreateTransactionSupplier.setMaxTransactionFee(1);
        accountCreateTransactionSupplier.setReceiverSignatureRequired(true);
        accountCreateTransactionSupplier.setPublicKey(key.toString());
        AccountCreateTransaction actual = accountCreateTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, AccountCreateTransaction::getInitialBalance)
                .returns(key, AccountCreateTransaction::getKey)
                .returns(ONE_TINYBAR, AccountCreateTransaction::getMaxTransactionFee)
                .returns(true, AccountCreateTransaction::getReceiverSignatureRequired)
                .extracting(AccountCreateTransaction::getAccountMemo, STRING)
                .contains("Mirror node created test account");
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return AccountCreateTransactionSupplier.class;
    }
}
