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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class AccountUpdateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        AccountUpdateTransactionSupplier accountUpdateTransactionSupplier = new AccountUpdateTransactionSupplier();
        accountUpdateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        AccountUpdateTransaction actual = accountUpdateTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, AccountUpdateTransaction::getAccountId)
                .returns(null, AccountUpdateTransaction::getKey)
                .returns(MAX_TRANSACTION_FEE_HBAR, AccountUpdateTransaction::getMaxTransactionFee)
                .returns(null, AccountUpdateTransaction::getProxyAccountId)
                .returns(false, AccountUpdateTransaction::getReceiverSignatureRequired)
                .satisfies(a -> assertThat(a.getAccountMemo()).contains("Mirror node updated test account"))
                .satisfies(a -> assertThat(a.getExpirationTime()).isNotNull())
        ;
    }

    @Test
    void createWithCustomData() {
        Instant expirationTime = Instant.now().plus(1, ChronoUnit.DAYS);
        PublicKey key = PrivateKey.generate().getPublicKey();

        AccountUpdateTransactionSupplier accountUpdateTransactionSupplier = new AccountUpdateTransactionSupplier();
        accountUpdateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        accountUpdateTransactionSupplier.setExpirationTime(expirationTime);
        accountUpdateTransactionSupplier.setMaxTransactionFee(1);
        accountUpdateTransactionSupplier.setProxyAccountId(ACCOUNT_ID_2.toString());
        accountUpdateTransactionSupplier.setPublicKey(key.toString());
        accountUpdateTransactionSupplier.setReceiverSignatureRequired(true);
        AccountUpdateTransaction actual = accountUpdateTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, AccountUpdateTransaction::getAccountId)
                .returns(expirationTime, AccountUpdateTransaction::getExpirationTime)
                .returns(key, AccountUpdateTransaction::getKey)
                .returns(ONE_TINYBAR, AccountUpdateTransaction::getMaxTransactionFee)
                .returns(ACCOUNT_ID_2, AccountUpdateTransaction::getProxyAccountId)
                .returns(true, AccountUpdateTransaction::getReceiverSignatureRequired)
                .satisfies(a -> assertThat(a.getAccountMemo()).contains("Mirror node updated test account"));
    }
}
