package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.hashgraph.sdk.account.TransferTransaction;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PublicKey;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;

@Log4j2
public class AccountClient extends AbstractNetworkClient {

    private ExpandedAccountId tokenTreasuryAccount = null;

    public AccountClient(SDKClient sdkClient) {
        super(sdkClient);
        log.debug("Creating Account Client");
    }

    public ExpandedAccountId getTokenTreasuryAccount() throws HederaStatusException {
        if (tokenTreasuryAccount == null) {
            tokenTreasuryAccount = createNewAccount(1_000_000_000L);
            log.debug("Treasury Account: {} will be used for current test session", tokenTreasuryAccount);
        }

        return tokenTreasuryAccount;
    }

    public static long getBalance(Client client, String accountIdString) throws HederaStatusException {
        return getBalance(client, accountIdString);
    }

    @Override
    public long getBalance() throws HederaStatusException {
        return getBalance(sdkClient.getOperatorId());
    }

    public long getBalance(AccountId accountId) throws HederaStatusException {
        Hbar balance = new AccountBalanceQuery()
                .setAccountId(accountId)
                .execute(client);

        log.debug("{} balance is {}", accountId, balance);

        return balance.asTinybar();
    }

    public TransactionReceipt sendCryptoTransfer(AccountId recipient, long amount) throws HederaStatusException {
        log.debug("Send CryptoTransfer of {} tℏ from {} to {}", amount, sdkClient.getOperatorId(), recipient);

        TransferTransaction cryptoTransferTransaction = new TransferTransaction()
                .addHbarTransfer(sdkClient.getOperatorId(), Math.negateExact(amount))
                .addHbarTransfer(recipient, amount)
                .setTransactionMemo("transfer test");

        TransactionReceipt transactionReceipt = executeTransactionAndRetrieveReceipt(cryptoTransferTransaction, null)
                .getReceipt();

        log.debug("Sent CryptoTransfer");

        return transactionReceipt;
    }

    public ExpandedAccountId createNewAccount(long initialBalance) throws HederaStatusException {
        // 1. Generate a Ed25519 private, public key pair
        Ed25519PrivateKey privateKey = Ed25519PrivateKey.generate();
        Ed25519PublicKey publicKey = privateKey.publicKey;

        log.debug("Private key = {}", privateKey);
        log.debug("Public key = {}", publicKey);

        Transaction tx = new AccountCreateTransaction()
                // The only _required_ property here is `key`
                .setKey(publicKey)
                .setInitialBalance(Hbar.from(initialBalance, HbarUnit.Tinybar))
                .build(client);

        // This will wait for the receipt to become available
        TransactionReceipt receipt = tx.execute(client).getReceipt(client);

        AccountId newAccountId = receipt.getAccountId();

        log.debug("Created new account {}", newAccountId);
        return new ExpandedAccountId(newAccountId, privateKey, publicKey);
    }
}
