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

import com.hedera.hashgraph.sdk.TokenUnpauseTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenUnpauseTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenUnpauseTransactionSupplier tokenUnpauseTransactionSupplier = new TokenUnpauseTransactionSupplier();

        tokenUnpauseTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUnpauseTransaction actual = tokenUnpauseTransactionSupplier.get();

        assertThat(actual)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenUnpauseTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenUnpauseTransaction::getTokenId);
    }

    @Test
    void createWithCustomData() {
        TokenUnpauseTransactionSupplier tokenUnpauseTransactionSupplier = new TokenUnpauseTransactionSupplier();
        tokenUnpauseTransactionSupplier.setMaxTransactionFee(1);
        tokenUnpauseTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenUnpauseTransaction actual = tokenUnpauseTransactionSupplier.get();

        assertThat(actual)
                .returns(ONE_TINYBAR, TokenUnpauseTransaction::getMaxTransactionFee)
                .returns(TOKEN_ID, TokenUnpauseTransaction::getTokenId);
    }

    @Override
    protected Class getSupplierClass() {
        return TokenFreezeTransactionSupplier.class;
    }
}
