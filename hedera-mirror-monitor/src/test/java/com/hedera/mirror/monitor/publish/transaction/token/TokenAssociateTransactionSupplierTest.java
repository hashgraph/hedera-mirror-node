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
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.TokenAssociateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenAssociateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenAssociateTransactionSupplier tokenAssociateTransactionSupplier = new TokenAssociateTransactionSupplier();
        tokenAssociateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenAssociateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenAssociateTransaction actual = tokenAssociateTransactionSupplier.get();

        TokenAssociateTransaction expected = new TokenAssociateTransaction()
                .setAccountId(ACCOUNT_ID)
                .setMaxTransactionFee(MAX_TRANSACTION_FEE_HBAR)
                .setTokenIds(List.of(TOKEN_ID))
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node associated test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }

    @Test
    void createWithCustomData() {
        TokenAssociateTransactionSupplier tokenAssociateTransactionSupplier = new TokenAssociateTransactionSupplier();
        tokenAssociateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenAssociateTransactionSupplier.setMaxTransactionFee(1);
        tokenAssociateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenAssociateTransaction actual = tokenAssociateTransactionSupplier.get();

        TokenAssociateTransaction expected = new TokenAssociateTransaction()
                .setAccountId(ACCOUNT_ID)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTokenIds(List.of(TOKEN_ID))
                .setTransactionMemo(actual.getTransactionMemo());

        assertAll(
                () -> assertThat(actual.getTransactionMemo()).contains("Mirror node associated test token"),
                () -> assertThat(actual).usingRecursiveComparison().isEqualTo(expected)
        );
    }
}
