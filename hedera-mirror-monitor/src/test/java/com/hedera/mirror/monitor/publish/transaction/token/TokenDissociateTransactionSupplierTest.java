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

import java.util.List;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.TokenDissociateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenDissociateTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenDissociateTransactionSupplier tokenDissociateTransactionSupplier =
                new TokenDissociateTransactionSupplier();
        tokenDissociateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenDissociateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenDissociateTransaction actual = tokenDissociateTransactionSupplier.get();

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node dissociated test token"))
                .returns(ACCOUNT_ID, TokenDissociateTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenDissociateTransaction::getMaxTransactionFee)
                .returns(List.of(TOKEN_ID), TokenDissociateTransaction::getTokenIds);
    }

    @Test
    void createWithCustomData() {
        TokenDissociateTransactionSupplier tokenDissociateTransactionSupplier =
                new TokenDissociateTransactionSupplier();
        tokenDissociateTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenDissociateTransactionSupplier.setMaxTransactionFee(1);
        tokenDissociateTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenDissociateTransaction actual = tokenDissociateTransactionSupplier.get();

        TokenDissociateTransaction expected = new TokenDissociateTransaction()
                .setAccountId(ACCOUNT_ID)
                .setMaxTransactionFee(ONE_TINYBAR)
                .setTokenIds(List.of(TOKEN_ID))
                .setTransactionMemo(actual.getTransactionMemo());

        assertThat(actual)
                .satisfies(a -> assertThat(a.getTransactionMemo()).contains("Mirror node dissociated test token"))
                .returns(ACCOUNT_ID, TokenDissociateTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenDissociateTransaction::getMaxTransactionFee)
                .returns(List.of(TOKEN_ID), TokenDissociateTransaction::getTokenIds);
    }
}
