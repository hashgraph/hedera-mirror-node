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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenGrantKycTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Data
public class TokenGrantKycTransactionSupplier implements TransactionSupplier<TokenGrantKycTransaction> {

    //Required
    @NotBlank
    private String accountId;

    @NotBlank
    private String tokenId;

    //Optional
    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenGrantKycTransaction get() {

        return new TokenGrantKycTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(maxTransactionFee)
                .setTokenId(TokenId.fromString(tokenId))
                .setTransactionMemo(Utility.getMemo("Mirror node granted kyc to test token"));
    }
}
