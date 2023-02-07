package com.hedera.mirror.monitor.publish.transaction.account;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.validation.constraints.Future;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.mirror.monitor.publish.transaction.TransactionSupplier;
import com.hedera.mirror.monitor.util.Utility;

@Data
public class AccountUpdateTransactionSupplier implements TransactionSupplier<AccountUpdateTransaction> {

    @NotBlank
    private String accountId;

    @NotNull
    @Future
    private Instant expirationTime = Instant.now().plus(120, ChronoUnit.DAYS);

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    private String proxyAccountId;

    private String publicKey;

    private boolean receiverSignatureRequired = false;

    @Override
    public AccountUpdateTransaction get() {
        AccountUpdateTransaction transaction = new AccountUpdateTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setAccountMemo(Utility.getMemo("Mirror node updated test account"))
                .setExpirationTime(expirationTime)
                .setMaxTransactionFee(Hbar.fromTinybars(maxTransactionFee))
                .setReceiverSignatureRequired(receiverSignatureRequired);

        if (proxyAccountId != null) {
            transaction.setProxyAccountId(AccountId.fromString(proxyAccountId));
        }
        if (publicKey != null) {
            transaction.setKey(PublicKey.fromString(publicKey));
        }
        return transaction;
    }
}
