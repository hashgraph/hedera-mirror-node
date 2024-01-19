/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.publish.transaction.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hashgraph.sdk.TokenGrantKycTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import org.junit.jupiter.api.Test;

class TokenGrantKycTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenGrantKycTransactionSupplier tokenGrantKycTransactionSupplier = new TokenGrantKycTransactionSupplier();
        tokenGrantKycTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenGrantKycTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenGrantKycTransaction actual = tokenGrantKycTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenGrantKycTransaction::getAccountId)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenGrantKycTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenGrantKycTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenGrantKycTransactionSupplier tokenGrantKycTransactionSupplier = new TokenGrantKycTransactionSupplier();
        tokenGrantKycTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenGrantKycTransactionSupplier.setMaxTransactionFee(1);
        tokenGrantKycTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenGrantKycTransaction actual = tokenGrantKycTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenGrantKycTransaction::getAccountId)
                .returns(ONE_TINYBAR, TokenGrantKycTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenGrantKycTransaction::getTokenId);
    }

    @Override
    protected Class<? extends TransactionSupplier<?>> getSupplierClass() {
        return TokenGrantKycTransactionSupplier.class;
    }
}
