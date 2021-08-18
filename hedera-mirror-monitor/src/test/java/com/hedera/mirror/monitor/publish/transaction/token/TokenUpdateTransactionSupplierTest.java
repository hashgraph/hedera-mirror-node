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

        assertThat(actual)
                .returns(null, TokenUpdateTransaction::getAdminKey)
                .returns(null, TokenUpdateTransaction::getAutoRenewAccountId)
                .returns(Duration.ofSeconds(8000000), TokenUpdateTransaction::getAutoRenewPeriod)
                .returns(null, TokenUpdateTransaction::getExpirationTime)
                .returns(null, TokenUpdateTransaction::getFeeScheduleKey)
                .returns(null, TokenUpdateTransaction::getFreezeKey)
                .returns(null, TokenUpdateTransaction::getKycKey)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenUpdateTransaction::getMaxTransactionFee)
                .returns(null, TokenUpdateTransaction::getSupplyKey)
                .returns(TOKEN_ID, TokenUpdateTransaction::getTokenId)
                .returns("HMNT_name", TokenUpdateTransaction::getTokenName)
                .returns("HMNT", TokenUpdateTransaction::getTokenSymbol)
                .returns(null, TokenUpdateTransaction::getTreasuryAccountId)
                .returns(null, TokenUpdateTransaction::getWipeKey)
                .satisfies(a -> assertThat(a.getTokenMemo()).contains("Mirror node updated test token"))
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node updated test token"));
    }

    @Test
    void createWithCustomData() {
        Duration autoRenewPeriod = Duration.ofSeconds(1);
        Instant expirationTime = Instant.now().plus(1, ChronoUnit.DAYS);
        PublicKey key = PrivateKey.generate().getPublicKey();

        TokenUpdateTransactionSupplier tokenUpdateTransactionSupplier = new TokenUpdateTransactionSupplier();
        tokenUpdateTransactionSupplier.setAdminKey(key.toString());
        tokenUpdateTransactionSupplier.setAutoRenewPeriod(autoRenewPeriod);
        tokenUpdateTransactionSupplier.setMaxTransactionFee(1);
        tokenUpdateTransactionSupplier.setSymbol("TEST");
        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenUpdateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());

        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUpdateTransaction actual = tokenUpdateTransactionSupplier.get();
        assertThat(actual)
                .returns(key, TokenUpdateTransaction::getAdminKey)
                .returns(ACCOUNT_ID, TokenUpdateTransaction::getAutoRenewAccountId)
                .returns(autoRenewPeriod, TokenUpdateTransaction::getAutoRenewPeriod)
                .returns(null, TokenUpdateTransaction::getExpirationTime)
                .returns(key, TokenUpdateTransaction::getFeeScheduleKey)
                .returns(key, TokenUpdateTransaction::getFreezeKey)
                .returns(key, TokenUpdateTransaction::getKycKey)
                .returns(ONE_TINYBAR, TokenUpdateTransaction::getMaxTransactionFee)
                .returns(key, TokenUpdateTransaction::getSupplyKey)
                .returns(TOKEN_ID, TokenUpdateTransaction::getTokenId)
                .returns("TEST_name", TokenUpdateTransaction::getTokenName)
                .returns("TEST", TokenUpdateTransaction::getTokenSymbol)
                .returns(ACCOUNT_ID, TokenUpdateTransaction::getTreasuryAccountId)
                .returns(key, TokenUpdateTransaction::getWipeKey)
                .satisfies(a -> assertThat(a.getTokenMemo()).contains("Mirror node updated test token"))
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node updated test token"));
    }

    @Test
    void createWithExpiration() {
        TokenUpdateTransactionSupplier tokenUpdateTransactionSupplier = new TokenUpdateTransactionSupplier();
        tokenUpdateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenUpdateTransactionSupplier.setExpirationTime(Instant.MAX);
        TokenUpdateTransaction actual = tokenUpdateTransactionSupplier.get();

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTokenMemo()).contains("Mirror node updated test token"))
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node updated test token"))
                .returns(null, TokenUpdateTransaction::getAdminKey)
                .returns(null, TokenUpdateTransaction::getAutoRenewAccountId)
                .returns(null, TokenUpdateTransaction::getAutoRenewPeriod)
                .returns(Instant.MAX, TokenUpdateTransaction::getExpirationTime)
                .returns(null, TokenUpdateTransaction::getFeeScheduleKey)
                .returns(null, TokenUpdateTransaction::getFreezeKey)
                .returns(null, TokenUpdateTransaction::getKycKey)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenUpdateTransaction::getMaxTransactionFee)
                .returns(null, TokenUpdateTransaction::getSupplyKey)
                .returns(TOKEN_ID, TokenUpdateTransaction::getTokenId)
                .returns("HMNT_name", TokenUpdateTransaction::getTokenName)
                .returns("HMNT", TokenUpdateTransaction::getTokenSymbol)
                .returns(null, TokenUpdateTransaction::getTreasuryAccountId)
                .returns(null, TokenUpdateTransaction::getWipeKey);
    }
}
