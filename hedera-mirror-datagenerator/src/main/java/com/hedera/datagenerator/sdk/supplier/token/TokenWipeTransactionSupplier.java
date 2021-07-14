package com.hedera.datagenerator.sdk.supplier.token;

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

import java.util.concurrent.atomic.AtomicLong;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenType;
import com.hedera.hashgraph.sdk.TokenWipeTransaction;

@Data
public class TokenWipeTransactionSupplier implements TransactionSupplier<TokenWipeTransaction> {

    @NotBlank
    private String accountId;

    @Min(1)
    private long amount = 1;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotNull
    private final AtomicLong serialNumber = new AtomicLong(1); // The serial number to transfer.  Increments over time.

    @NotBlank
    private String tokenId;

    @NotNull
    private TokenType type = TokenType.FUNGIBLE_COMMON;

    @Override
    public TokenWipeTransaction get() {

        TokenWipeTransaction transaction = new TokenWipeTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenId(TokenId.fromString(tokenId))
                .setTransactionMemo(Utility.getMemo("Mirror node wiped test token"));

        switch (type) {
            case FUNGIBLE_COMMON:
                transaction.setAmount(amount);
                break;
            case NON_FUNGIBLE_UNIQUE:
                for (int i = 0; i < amount; i++) {
                    transaction.addSerial(serialNumber.getAndIncrement());
                }
        }

        return transaction;
    }
}
