package com.hedera.mirror.monitor.publish.transaction.token;

import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import lombok.Data;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.TokenAssociateTransaction;
import com.hedera.hashgraph.sdk.TokenId;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

@Data
public class TokenAssociateTransactionSupplier implements TransactionSupplier<TokenAssociateTransaction> {

    @NotBlank
    private String accountId;

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    @NotBlank
    private String tokenId;

    @Override
    public TokenAssociateTransaction get() {

        return new TokenAssociateTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setTokenIds(List.of(TokenId.fromString(tokenId)));
    }
}
