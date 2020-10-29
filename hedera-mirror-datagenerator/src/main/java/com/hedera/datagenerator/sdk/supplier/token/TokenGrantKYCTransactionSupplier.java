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

import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenGrantKycTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class TokenGrantKYCTransactionSupplier implements TransactionSupplier<TokenGrantKycTransaction> {

    //Required
    private final String accountId;
    private final String tokenId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenGrantKycTransaction get() {

        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(tokenId)) {
            throw new TransactionSupplierException(this.getClass()
                    .getName() + " requires an accountId and a tokenId be provided");
        }

        return new TokenGrantKycTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(maxTransactionFee)
                .setTokenId(TokenId.fromString(tokenId))
                .setTransactionMemo(Utility
                        .getEncodedTimestamp() + "_Mirror node granted kyc to test token at " + Instant.now());
    }
}
