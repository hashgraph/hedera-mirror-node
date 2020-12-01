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
import java.util.Arrays;
import java.util.List;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import com.hedera.datagenerator.common.Utility;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.datagenerator.sdk.supplier.TransactionSupplierException;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.AccountUpdateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Data
public class AccountUpdateTransactionSupplier implements TransactionSupplier<AccountUpdateTransaction> {

    private static final List<String> requiredFields = Arrays.asList("accountId");

    //Required
    @NotBlank
    private String accountId;

    //Optional
    @NotNull
    private Duration autoRenewPeriod = Duration.ofSeconds(8000000);

    @NotNull
    private Instant expirationTime = Instant.now().plus(120, ChronoUnit.DAYS);

    @Min(1)
    private long maxTransactionFee = 1_000_000_000;

    private String proxyAccountId;

    private String publicKey;

    private boolean receiverSignatureRequired = false;

    @Override
    public AccountUpdateTransaction get() {

        if (StringUtils.isBlank(accountId)) {
            throw new TransactionSupplierException(this, requiredFields);
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
