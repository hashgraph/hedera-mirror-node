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

import java.security.SecureRandom;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.AdminKeyable;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenCreateTransaction;
import com.hedera.hashgraph.sdk.TokenSupplyType;
import com.hedera.hashgraph.sdk.TokenType;

@Data
public class TokenCreateTransactionSupplier implements TransactionSupplier<TokenCreateTransaction>, AdminKeyable {

    private static final SecureRandom RANDOM = new SecureRandom();

    private String adminKey;

    @Min(1)
    private int decimals = 10;

    private boolean freezeDefault = false;

    @Min(0)
    private int initialSupply = 1000000000;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String symbol = RANDOM.ints(5, 'A', 'Z')
            .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();

    @NotBlank
    private String treasuryAccountId;

    TokenType tokenType = TokenType.FUNGIBLE_COMMON;

    TokenSupplyType tokenSupplyType = TokenSupplyType.INFINITE;

    Long maxSupply;

    @Override
    public TokenCreateTransaction get() {
        String memo = Utility.getMemo("Mirror node created test token");
        AccountId treasuryAccount = AccountId.fromString(treasuryAccountId);
        TokenCreateTransaction tokenCreateTransaction = new TokenCreateTransaction()
                .setAutoRenewAccountId(treasuryAccount)
                .setDecimals(decimals)
                .setFreezeDefault(freezeDefault)
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenMemo(memo)
                .setTokenName(symbol + "_name")
                .setTokenSymbol(symbol)
                .setTransactionMemo(memo)
                .setTreasuryAccountId(treasuryAccount)
                .setTokenType(tokenType)
                .setSupplyType(tokenSupplyType);

        if (adminKey != null) {
            PublicKey key = PublicKey.fromString(adminKey);
            tokenCreateTransaction.setAdminKey(key).setFreezeKey(key).setKycKey(key).setSupplyKey(key).setWipeKey(key);
        }
        if (maxSupply != null) {
            tokenCreateTransaction.setMaxSupply(maxSupply);
        }

        if (tokenType == TokenType.FUNGIBLE_COMMON) {
            tokenCreateTransaction.setInitialSupply(initialSupply);
        }

        return tokenCreateTransaction;
    }
}
