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

import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.token.TokenDissociateTransaction;
import com.hedera.hashgraph.sdk.token.TokenId;

@Data
public class TokenDissociateTransactionSupplier implements TransactionSupplier<TokenDissociateTransaction> {

    private static final List<String> requiredFields = Arrays.asList("accountId", "tokenId");

    //Required
    @NotBlank
    private String accountId;

    @NotBlank
    private String tokenId;

    //Optional
    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @Override
    public TokenDissociateTransaction get() {

        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(tokenId)) {
            throw new TransactionSupplierException(this, requiredFields);
        }

        return new TokenDissociateTransaction()
                .addTokenId(TokenId.fromString(tokenId))
                .setAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo(Utility.getMemo("Mirror node dissociated test token"));
    }
}
