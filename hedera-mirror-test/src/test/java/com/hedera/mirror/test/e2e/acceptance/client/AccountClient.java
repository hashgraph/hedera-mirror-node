package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HbarUnit;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.account.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.account.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;

@Log4j2
public class AccountClient {

    public static AccountId createNewAccount(Client client) throws HederaStatusException {
        // 1. Generate a Ed25519 private, public key pair
        Ed25519PrivateKey newKey = Ed25519PrivateKey.generate();
        Ed25519PublicKey newPublicKey = newKey.publicKey;

        log.info("Private key = {}", newKey);
        log.info("Public key = {}", newPublicKey);

        Transaction tx = new AccountCreateTransaction()
                // The only _required_ property here is `key`
                .setKey(newKey.publicKey)
                .setInitialBalance(Hbar.from(1, HbarUnit.Tinybar))
                .build(client);

        tx.execute(client);

        // This will wait for the receipt to become available
        TransactionReceipt receipt = tx.getReceipt(client);

        AccountId newAccountId = receipt.getAccountId();

        log.trace("account = {}", newAccountId);
        return newAccountId;
    }

    public static long getBalance(Client client, String accountIdString) throws HederaStatusException {
        return getBalance(client, accountIdString);
    }

    public static long getBalance(Client client, AccountId accountId) throws HederaStatusException {
        Hbar balance = new AccountBalanceQuery()
                .setAccountId(accountId)
                .execute(client);

        log.debug("{} balance is {}", accountId, balance);

        return balance.asTinybar();
    }
}
