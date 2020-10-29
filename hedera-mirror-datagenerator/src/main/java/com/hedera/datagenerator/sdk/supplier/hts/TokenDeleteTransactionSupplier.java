package com.hedera.datagenerator.sdk.supplier.hts;

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

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.token.TokenDeleteTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Builder
@Value
public class TokenDeleteTransactionSupplier implements TransactionSupplier<TokenDeleteTransaction> {
    //Required
    private final TokenId tokenId;

    //Optional
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenDeleteTransaction get() {
        return new TokenDeleteTransaction()
                .setTokenId(tokenId)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier Delete token_" + Instant.now());
    }
}
