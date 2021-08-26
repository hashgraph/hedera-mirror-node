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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TokenWipeTransaction;
import com.hedera.mirror.monitor.publish.transaction.AbstractTransactionSupplierTest;

class TokenWipeTransactionSupplierTest extends AbstractTransactionSupplierTest {

    @Test
    void createWithMinimumData() {
        TokenWipeTransactionSupplier tokenWipeTransactionSupplier = new TokenWipeTransactionSupplier();
        tokenWipeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenWipeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        TokenWipeTransaction actual = tokenWipeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenWipeTransaction::getAccountId)
                .returns(1L, TokenWipeTransaction::getAmount)
                .returns(MAX_TRANSACTION_FEE_HBAR, TokenWipeTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenWipeTransaction::getSerials)
                .returns(TOKEN_ID, TokenWipeTransaction::getTokenId);
    }

    @Test
    void createWithCustomFungibleData() {
        TokenWipeTransactionSupplier tokenWipeTransactionSupplier = new TokenWipeTransactionSupplier();
        tokenWipeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenWipeTransactionSupplier.setAmount(2);
        tokenWipeTransactionSupplier.setMaxTransactionFee(1);
        tokenWipeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenWipeTransactionSupplier.setType(TokenType.FUNGIBLE_COMMON);
        TokenWipeTransaction actual = tokenWipeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenWipeTransaction::getAccountId)
                .returns(2L, TokenWipeTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenWipeTransaction::getMaxTransactionFee)
                .returns(Collections.emptyList(), TokenWipeTransaction::getSerials)
                .returns(TOKEN_ID, TokenWipeTransaction::getTokenId);
    }

    @Test
    void createWithCustomNonFungibleData() {
        TokenWipeTransactionSupplier tokenWipeTransactionSupplier = new TokenWipeTransactionSupplier();
        tokenWipeTransactionSupplier.setAccountId(ACCOUNT_ID.toString());
        tokenWipeTransactionSupplier.setAmount(2);
        tokenWipeTransactionSupplier.setMaxTransactionFee(1);
        tokenWipeTransactionSupplier.setSerialNumber(new AtomicLong(10));
        tokenWipeTransactionSupplier.setTokenId(TOKEN_ID.toString());
        tokenWipeTransactionSupplier.setType(TokenType.NON_FUNGIBLE_UNIQUE);
        TokenWipeTransaction actual = tokenWipeTransactionSupplier.get();

        assertThat(actual)
                .returns(ACCOUNT_ID, TokenWipeTransaction::getAccountId)
                .returns(0L, TokenWipeTransaction::getAmount)
                .returns(ONE_TINYBAR, TokenWipeTransaction::getMaxTransactionFee)
                .returns(List.of(10L, 11L), TokenWipeTransaction::getSerials)
                .returns(TOKEN_ID, TokenWipeTransaction::getTokenId);
    }

    @Override
    protected Class getSupplierClass() {
        return TokenWipeTransactionSupplier.class;
    }
}
