package com.hedera.mirror.monitor.publish.transaction.token;

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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenUpdateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenUpdateTransactionSupplier tokenUpdateTransactionSupplier = new TokenUpdateTransactionSupplier();
        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUpdateTransaction actual = tokenUpdateTransactionSupplier.get();

        TokenUpdateTransaction expected = new TokenUpdateTransaction()
                .setAutoRenewPeriod(actual.getAutoRenewPeriod())
                .setExpirationTime(actual.getExpirationTime())
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setTokenMemo(actual.getTokenMemo())
                .setTokenName("HMNT_name")
                .setTokenSymbol("HMNT")
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTokenMemo());

        assertAll(
                () -> assertThat(actual.getAutoRenewPeriod()).isNotNull(),
                () -> assertThat(actual.getExpirationTime()).isNotNull(),
                () -> assertThat(actual.getTokenMemo()).contains("Mirror node updated test token"),
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node updated test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        Duration autoRenewPeriod = Duration.ofSeconds(1);
        Instant expirationTime = Instant.now().plus(1, ChronoUnit.DAYS);
        PublicKey key = PrivateKey.generate().getPublicKey();

        TokenUpdateTransactionSupplier tokenUpdateTransactionSupplier = new TokenUpdateTransactionSupplier();
        tokenUpdateTransactionSupplier.setAdminKey(key.toString());
        tokenUpdateTransactionSupplier.setAutoRenewPeriod(autoRenewPeriod);
        tokenUpdateTransactionSupplier.setExpirationTime(expirationTime);
        tokenUpdateTransactionSupplier.setMaxTransactionFee(1);
        tokenUpdateTransactionSupplier.setSymbol("TEST");
        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenUpdateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());

        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUpdateTransaction actual = tokenUpdateTransactionSupplier.get();

        TokenUpdateTransaction expected = new TokenUpdateTransaction()
                .setAdminKey(key)
                .setAutoRenewAccountId(ACCOUNT_ID)
                .setAutoRenewPeriod(autoRenewPeriod)
                .setExpirationTime(expirationTime)
                .setFreezeKey(key)
                .setKycKey(key)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setSupplyKey(key)
                .setTokenMemo(actual.getTokenMemo())
                .setTokenName("TEST_name")
                .setTokenSymbol("TEST")
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTokenMemo())
                .setTreasuryAccountId(ACCOUNT_ID)
                .setWipeKey(key);

        assertAll(
                () -> assertThat(actual.getTokenMemo()).contains("Mirror node updated test token"),
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node updated test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
