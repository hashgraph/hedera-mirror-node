package com.hedera.datagenerator.sdk.supplier.token;

/*-
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.Builder;
import lombok.Value;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.hashgraph.sdk.token.TokenUpdateTransaction;

@Builder
@Value
public class TokenUpdateTransactionSupplier implements TransactionSupplier<TokenUpdateTransaction> {
    //Required
    private final String tokenId;
    private final String treasuryAccountId;

    //Optional
    private final String adminKey;

    @Builder.Default
    private final long autoRenewPeriodSeconds = 8000000;

    @Builder.Default
    private final long expirationTime = 120;

    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Builder.Default
    private final String symbol = "HMNT";

    @Override
    public TokenUpdateTransaction get() {
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setAutoRenewPeriod(Duration.ofSeconds(autoRenewPeriodSeconds))
                .setExpirationTime(Instant.now().plus(expirationTime, ChronoUnit.DAYS))
                .setMaxTransactionFee(maxTransactionFee)
                .setName(symbol + "_name")
                .setSybmol(symbol)
                .setTokenId(TokenId.fromString(tokenId))
                .setTransactionMemo("Mirror node updated test token at " + Instant.now());

        if (adminKey != null) {
            Ed25519PublicKey key = Ed25519PublicKey.fromString(adminKey);
            tokenUpdateTransaction
                    .setAdminKey(key)
                    .setFreezeKey(key)
                    .setKycKey(key)
                    .setSupplyKey(key)
                    .setWipeKey(key);
        }
        if (treasuryAccountId != null) {
            AccountId treastury = AccountId.fromString(treasuryAccountId);
            tokenUpdateTransaction
                    .setAutoRenewAccount(treastury)
                    .setTreasury(treastury);
        }
        return tokenUpdateTransaction;
    }
}
