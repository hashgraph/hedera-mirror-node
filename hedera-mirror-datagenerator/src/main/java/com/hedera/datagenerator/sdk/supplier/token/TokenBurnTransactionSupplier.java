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

import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.token.TokenBurnTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class TokenBurnTransactionSupplier implements TransactionSupplier<TokenBurnTransaction> {

    //Required
    private final String tokenId;

    //Optional
    @Builder.Default
    private final long amount = 1;

    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenBurnTransaction get() {

        if (StringUtils.isBlank(tokenId)) {
            throw new TransactionSupplierException(this.getClass()
                    .getName() + " requires a tokenId be provided");
        }

        return new TokenBurnTransaction()
                .setAmount(amount)
                .setMaxTransactionFee(maxTransactionFee)
                .setTokenId(TokenId.fromString(tokenId))
                .setTransactionMemo(Utility.getMemo("Mirror node burned test token"));
    }
}
