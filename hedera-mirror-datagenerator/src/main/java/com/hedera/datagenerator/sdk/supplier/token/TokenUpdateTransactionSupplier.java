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
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.hashgraph.sdk.token.TokenUpdateTransaction;

@Builder
@Value
public class TokenUpdateTransactionSupplier implements TransactionSupplier<TokenUpdateTransaction> {

    private static final List<String> requiredFields = Arrays.asList("tokenId");

    //Required
    private final String tokenId;

    //Optional
    private final String adminKey;

    @Builder.Default
    private final Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @Builder.Default
    private final Instant expirationTime = Instant.now().plus(120, ChronoUnit.DAYS);

    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Builder.Default
    private final String symbol = "HMNT";
    private final String treasuryAccountId;

    @Override
    public TokenUpdateTransaction get() {

        if (StringUtils.isBlank(tokenId)) {
            throw new TransactionSupplierException(this, requiredFields);
        }

        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setAutoRenewPeriod(autoRenewPeriod)
                .setExpirationTime(expirationTime)
                .setMaxTransactionFee(maxTransactionFee)
                .setName(symbol + "_name")
                .setSymbol(symbol)
                .setTokenId(TokenId.fromString(tokenId))
                .setTransactionMemo(Utility.getMemo("Mirror node updated test token"));

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
