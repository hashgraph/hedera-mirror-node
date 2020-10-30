package com.hedera.datagenerator.sdk.supplier.account;

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
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Builder
@Value
@Log4j2
public class AccountUpdateTransactionSupplier implements TransactionSupplier<AccountUpdateTransaction> {

    //Required
    private final String accountId;

    //Optional
    @Builder.Default
    private final Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @Builder.Default
    private final Instant expirationTime = Instant.now().plus(120, ChronoUnit.DAYS);

    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;
    private final String proxyAccountId;
    private final String publicKey;

    @Builder.Default
    private final boolean receiverSignatureRequired = false;

    @Override
    public AccountUpdateTransaction get() {

        if (StringUtils.isBlank(accountId)) {
            throw new TransactionSupplierException(this.getClass()
                    .getSimpleName() + " requires an accountId be provided");
        }

        AccountUpdateTransaction transaction = new AccountUpdateTransaction()
                .setAccountId(AccountId.fromString(accountId))
                .setAutoRenewPeriod(autoRenewPeriod)
                .setExpirationTime(expirationTime)
                .setMaxTransactionFee(maxTransactionFee)
                .setReceiverSignatureRequired(receiverSignatureRequired)
                .setTransactionMemo(Utility.getMemo("Mirror node updated test account"));

        if (proxyAccountId != null) {
            transaction.setProxyAccountId(AccountId.fromString(proxyAccountId));
        }
        if (publicKey != null) {
            transaction.setKey(Ed25519PublicKey.fromString(publicKey));
        }
        return transaction;
    }
}
