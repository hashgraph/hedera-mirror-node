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

import java.time.Instant;
import lombok.Builder;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.datagenerator.sdk.supplier.TransactionSupplier;
import com.hedera.hashgraph.sdk.account.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Builder
@Value
@Log4j2
public class AccountCreateTransactionSupplier implements TransactionSupplier<AccountCreateTransaction> {

    //Optional
    private final Ed25519PublicKey publicKey;
    @Builder.Default
    private final long initialBalance = 10_000_000;
    @Builder.Default
    private final long maxTransactionFee = 1_000_000_000;

    @Override
    public AccountCreateTransaction get() {
        return new AccountCreateTransaction()
                .setKey(publicKey != null ? publicKey : generateKeys())
                .setInitialBalance(initialBalance)
                .setMaxTransactionFee(maxTransactionFee)
                .setTransactionMemo("Supplier create account_" + Instant.now());
    }

    private Ed25519PublicKey generateKeys() {
        Ed25519PrivateKey privateKey = Ed25519PrivateKey.generate();
        Ed25519PublicKey publicKey = privateKey.publicKey;

        log.debug("Private key = {}", privateKey);
        log.debug("Public key = {}", publicKey);
        return publicKey;
    }
}
