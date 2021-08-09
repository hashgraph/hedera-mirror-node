package com.hedera.mirror.monitor.publish.transaction.token;

/*
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.TokenBurnTransaction;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenBurnTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenBurnTransactionSupplier tokenBurnTransactionSupplier = new TokenBurnTransactionSupplier();
        tokenBurnTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenBurnTransaction actual = tokenBurnTransactionSupplier.get();

        TokenBurnTransaction expected = new TokenBurnTransaction()
                .setAmount(1)
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node burned test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomFungibleData() {
        TokenBurnTransactionSupplier tokenBurnTransactionSupplier = new TokenBurnTransactionSupplier();
        tokenBurnTransactionSupplier.setAmount(2);
        tokenBurnTransactionSupplier.setMaxTransactionFee(1);
        tokenBurnTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenBurnTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenBurnTransaction actual = tokenBurnTransactionSupplier.get();

        TokenBurnTransaction expected = new TokenBurnTransaction()
                .setAmount(2)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node burned test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomNonFungibleData() {
        TokenBurnTransactionSupplier tokenBurnTransactionSupplier = new TokenBurnTransactionSupplier();
        tokenBurnTransactionSupplier.setAmount(2);
        tokenBurnTransactionSupplier.setMaxTransactionFee(1);
        tokenBurnTransactionSupplier.setSerialNumber(new AtomicLong(10));
        tokenBurnTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenBurnTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenBurnTransaction actual = tokenBurnTransactionSupplier.get();

        TokenBurnTransaction expected = new TokenBurnTransaction()
                .setMaxTransactionFee(ONE_TINYBAR)
                .setSerials(List.of(10L, 11L))
                .setTokenId(TOKEN_ID)
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node burned test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
