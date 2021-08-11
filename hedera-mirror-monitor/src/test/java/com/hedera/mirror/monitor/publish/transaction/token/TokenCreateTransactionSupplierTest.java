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

import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenCreateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenCreateTransactionSupplier tokenCreateTransactionSupplier = new TokenCreateTransactionSupplier();
        tokenCreateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());
        TokenCreateTransaction actual = tokenCreateTransactionSupplier.get();

        TokenCreateTransaction expected = new TokenCreateTransaction()
                .setAutoRenewAccountId(ACCOUNT_ID)
                .setDecimals(10)
                .setFreezeDefault(false)
                .setInitialSupply(1000000000)
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setSupplyType(TokenSupplyType.INFINITE)
                .setTokenMemo(actual.getTokenMemo())
                .setTokenName(actual.getTokenName())
                .setTokenSymbol(actual.getTokenSymbol())
                .setTokenType(TokenType.FUNGIBLE_COMMON)
                .setTransactionMemo(actual.getTransactionMemo())
                .setTreasuryAccountId(ACCOUNT_ID);

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTokenMemo()).contains("Mirror node created test token"))
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test token"))
                .satisfies(a -> assertThat(a.getTokenSymbol().length()).isEqualTo(5))
                .satisfies(a -> assertThat(a.getTokenName()).contains("_name"))
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(expected));
    }

    @Test
    void createWithCustomFungibleData() {
        PublicKey key = PrivateKey.generate().getPublicKey();

        TokenCreateTransactionSupplier tokenCreateTransactionSupplier = new TokenCreateTransactionSupplier();
        tokenCreateTransactionSupplier.setAdminKey(key.toString());
        tokenCreateTransactionSupplier.setDecimals(1);
        tokenCreateTransactionSupplier.setFreezeDefault(true);
        tokenCreateTransactionSupplier.setInitialSupply(1);
        tokenCreateTransactionSupplier.setMaxSupply(1);
        tokenCreateTransactionSupplier.setMaxTransactionFee(1);
        tokenCreateTransactionSupplier.setSupplyType(TokenSupplyType.FINITE);
        tokenCreateTransactionSupplier.setSymbol("TEST");
        tokenCreateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());
        tokenCreateTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenCreateTransaction actual = tokenCreateTransactionSupplier.get();

        TokenCreateTransaction expected = new TokenCreateTransaction()
                .setAdminKey(key)
                .setAutoRenewAccountId(ACCOUNT_ID)
                .setDecimals(1)
                .setFreezeDefault(true)
                .setFreezeKey(key)
                .setInitialSupply(1)
                .setKycKey(key)
                .setMaxSupply(1)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setSupplyKey(key)
                .setSupplyType(TokenSupplyType.FINITE)
                .setTokenMemo(actual.getTokenMemo())
                .setTokenName("TEST_name")
                .setTokenSymbol("TEST")
                .setTokenType(TokenType.FUNGIBLE_COMMON)
                .setTransactionMemo(actual.getTransactionMemo())
                .setTreasuryAccountId(ACCOUNT_ID)
                .setWipeKey(key);

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTokenMemo()).contains("Mirror node created test token"))
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test token"))
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(expected));
    }

    @Test
    void createWithNonFungibleData() {
        PublicKey key = PrivateKey.generate().getPublicKey();

        TokenCreateTransactionSupplier tokenCreateTransactionSupplier = new TokenCreateTransactionSupplier();
        tokenCreateTransactionSupplier.setTreasuryAccountId(ACCOUNT_ID.toString());
        tokenCreateTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenCreateTransaction actual = tokenCreateTransactionSupplier.get();

        TokenCreateTransaction expected = new TokenCreateTransaction()
                .setAutoRenewAccountId(ACCOUNT_ID)
                .setFreezeDefault(false)
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setSupplyType(TokenSupplyType.INFINITE)
                .setTokenMemo(actual.getTokenMemo())
                .setTokenName(actual.getTokenName())
                .setTokenSymbol(actual.getTokenSymbol())
                .setTokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                .setTransactionMemo(actual.getTransactionMemo())
                .setTreasuryAccountId(ACCOUNT_ID);

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTokenMemo()).contains("Mirror node created test token"))
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node created test token"))
                .satisfies(a -> assertThat(a).usingRecursiveComparison().isEqualTo(expected));
    }
}
