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

import java.util.concurrent.TimeoutException;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;

@Log4j2
public class AccountClient extends AbstractNetworkClient {

    private ExpandedAccountId tokenTreasuryAccount = null;

    public AccountClient(SDKClient sdkClient) {
        super(sdkClient);
        log.debug("Creating Account Client");
    }

    public ExpandedAccountId getTokenTreasuryAccount() throws ReceiptStatusException, PrecheckStatusException,
            TimeoutException {
        if (tokenTreasuryAccount == null) {
            tokenTreasuryAccount = createNewAccount(1_000_000_000L);
            log.debug("Treasury Account: {} will be used for current test session", tokenTreasuryAccount);
        }

        return tokenTreasuryAccount;
    }

    public static long getBalance(Client client, String accountIdString) {
        return getBalance(client, accountIdString);
    }

    @Override
    public long getBalance() throws TimeoutException, PrecheckStatusException {
        return getBalance(sdkClient.getOperatorId());
    }

    public long getBalance(AccountId accountId) throws TimeoutException, PrecheckStatusException {
        Hbar balance = new AccountBalanceQuery()
                .setAccountId(accountId)
                .execute(client)
                .hbars;

        log.debug("{} balance is {}", accountId, balance);

        return balance.toTinybars();
    }

    public TransferTransaction getCryptoTransferTransaction(AccountId recipient, Hbar hbarAmount) {
        return new TransferTransaction()
                .addHbarTransfer(sdkClient.getOperatorId(), hbarAmount.negated())
                .addHbarTransfer(recipient, hbarAmount)
                .setTransactionMemo("transfer test");
    }

    public TransactionReceipt sendCryptoTransfer(AccountId recipient, Hbar hbarAmount) throws ReceiptStatusException,
            PrecheckStatusException, TimeoutException {
        log.debug("Send CryptoTransfer of {} tℏ from {} to {}", hbarAmount.toTinybars(), sdkClient
                .getOperatorId(), recipient);

        TransferTransaction cryptoTransferTransaction = getCryptoTransferTransaction(recipient, hbarAmount);

        TransactionReceipt transactionReceipt = executeTransactionAndRetrieveReceipt(cryptoTransferTransaction, null)
                .getReceipt();

        log.debug("Sent CryptoTransfer");

        return transactionReceipt;
    }

    public ExpandedAccountId createNewAccount(long initialBalance) throws TimeoutException, PrecheckStatusException,
            ReceiptStatusException {
        // 1. Generate a Ed25519 private, public key pair
        PrivateKey privateKey = PrivateKey.generate();
        PublicKey publicKey = privateKey.getPublicKey();

        log.debug("Private key = {}", privateKey);
        log.debug("Public key = {}", publicKey);

        TransactionResponse transactionResponse = new AccountCreateTransaction()
                // The only _required_ property here is `key`
                .setKey(publicKey)
                .setInitialBalance(Hbar.fromTinybars(initialBalance))
                .execute(client);

        // This will wait for the receipt to become available
        TransactionReceipt receipt = transactionResponse.getReceipt(client);

        AccountId newAccountId = receipt.accountId;

        log.debug("Created new account {}", newAccountId);
        return new ExpandedAccountId(newAccountId, privateKey, publicKey);
    }
}
