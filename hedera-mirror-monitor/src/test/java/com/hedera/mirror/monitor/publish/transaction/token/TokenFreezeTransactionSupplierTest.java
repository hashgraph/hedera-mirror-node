package com.hedera.mirror.monitor.publish.transaction.token;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.hedera.hashgraph.sdk.TokenFreezeTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenFreezeTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenFreezeTransactionSupplier tokenFreezeTransactionSupplier = new TokenFreezeTransactionSupplier();
        tokenFreezeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenFreezeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenFreezeTransaction actual = tokenFreezeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenFreezeTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenFreezeTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenFreezeTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenFreezeTransactionSupplier tokenFreezeTransactionSupplier = new TokenFreezeTransactionSupplier();
        tokenFreezeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenFreezeTransactionSupplier.setMaxTransactionFee(1);
        tokenFreezeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenFreezeTransaction actual = tokenFreezeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenFreezeTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenFreezeTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenFreezeTransaction::getTokenId);
    }

    @Override
    protected Class getSupplierClass() {
        return TokenFreezeTransactionSupplier.class;
    }
}
