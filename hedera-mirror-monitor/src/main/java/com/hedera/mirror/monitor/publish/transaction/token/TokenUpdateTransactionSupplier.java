/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.hashgraph.sdk.TokenUpdateTransaction;
import com.hedera.mirror.monitor.publish.transaction.AdminKeyable;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.util.Utility;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;

@Data
public class TokenUpdateTransactionSupplier implements TransactionSupplier<TokenUpdateTransaction>, AdminKeyable {

    private String adminKey;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @Future
    private Instant expirationTime;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank()
    private String symbol = "HMNT";

    @NotBlank
    private String tokenId;

    private String treasuryAccountId;

    @Override
    public TokenUpdateTransaction get() {
        TokenUpdateTransaction tokenUpdateTransaction = new TokenUpdateTransaction()
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenMemo(Utility.getMemo("Mirror node updated test token"))
                .setTokenName(symbol + "_name")
                .setTokenSymbol(symbol)
                .setTokenId(TokenId.fromString(tokenId));

        if (adminKey != null) {
            PublicKey key = PublicKey.fromString(adminKey);
            tokenUpdateTransaction
                    .setAdminKey(key)
                    .setFeeScheduleKey(key)
                    .setFreezeKey(key)
                    .setKycKey(key)
                    .setSupplyKey(key)
                    .setWipeKey(key);
        }
        if (treasuryAccountId != null) {
            AccountId treastury = AccountId.fromString(treasuryAccountId);
            tokenUpdateTransaction.setAutoRenewAccountId(treastury).setTreasuryAccountId(treastury);
        }

        if (expirationTime != null) {
            tokenUpdateTransaction.setExpirationTime(expirationTime);
        } else {
            tokenUpdateTransaction.setAutoRenewPeriod(autoRenewPeriod);
        }

        return tokenUpdateTransaction;
    }
}
