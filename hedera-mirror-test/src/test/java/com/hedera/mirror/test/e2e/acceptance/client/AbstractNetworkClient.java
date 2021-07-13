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

import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Data
public abstract class AbstractNetworkClient {
    protected final SDKClient sdkClient;
    protected final Client client;
    protected final RetryTemplate retryTemplate;

    public AbstractNetworkClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        this.sdkClient = sdkClient;
        client = sdkClient.getClient();
        this.retryTemplate = retryTemplate;
    }

    public TransactionId executeTransaction(Transaction transaction, KeyList keyList) {

        // set max retries on sdk
        transaction.setMaxAttempts(sdkClient.getAcceptanceTestProperties().getSdkProperties().getMaxAttempts());

        if (keyList != null) {
            transaction.freezeWith(client); // Signing requires transaction to be frozen
            for (Key k : keyList) {
                transaction.sign((PrivateKey) k);
            }
            log.debug("{} additional signatures added to transaction", keyList.size());
        }

        TransactionResponse transactionResponse = retryTemplate.execute(x -> executeTransaction(transaction));
        TransactionId transactionId = transactionResponse.transactionId;
        log.debug("Executed transaction {} with {} signatures.", transactionId, keyList == null ? 0 : keyList.size());

        return transactionId;
    }

    public TransactionId executeTransaction(Transaction transaction, ExpandedAccountId payer) {
        // set max retries on sdk
        transaction.setMaxAttempts(sdkClient.getAcceptanceTestProperties().getSdkProperties().getMaxAttempts());
        transaction.setTransactionId(TransactionId.generate(payer.getAccountId()));

        transaction.freezeWith(client);
        transaction.sign(payer.getPrivateKey());

        TransactionResponse response = retryTemplate.execute(x -> executeTransaction(transaction));
        TransactionId transactionId = response.transactionId;
        log.debug("Executed transaction {}", transactionId);

        return transactionId;
    }

    @SneakyThrows
    private TransactionResponse executeTransaction(Transaction transaction) {
        return (TransactionResponse) transaction.execute(client);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction transaction,
                                                                           KeyList keyList) {
        long startBalance = getBalance();
        TransactionId transactionId = executeTransaction(transaction, keyList);
        TransactionReceipt transactionReceipt = getTransactionReceipt(transactionId);
        log.trace("Executed transaction {} cost {} tℏ", transactionId, startBalance - getBalance());
        return new NetworkTransactionResponse(transactionId, transactionReceipt);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction transaction,
                                                                           ExpandedAccountId payer) {
        long startBalance = getBalance();
        TransactionId transactionId = executeTransaction(transaction, payer);
        TransactionReceipt transactionReceipt = getTransactionReceipt(transactionId);
        log.trace("Executed transaction {} cost {} tℏ", transactionId, startBalance - getBalance());
        return new NetworkTransactionResponse(transactionId, transactionReceipt);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction transaction) {
        long startBalance = getBalance();
        TransactionId transactionId = executeTransaction(transaction, (KeyList) null);
        TransactionReceipt transactionReceipt = getTransactionReceipt(transactionId);
        log.trace("Executed transaction {} cost {} tℏ", transactionId, startBalance - getBalance());
        return new NetworkTransactionResponse(transactionId, transactionReceipt);
    }

    @SneakyThrows
    public TransactionReceipt getTransactionReceipt(TransactionId transactionId) {
        return retryTemplate.execute(x -> transactionId.getReceipt(client));
    }

    @SneakyThrows
    public TransactionRecord getTransactionRecord(TransactionId transactionId) {
        return retryTemplate.execute(x -> transactionId.getRecord(client));
    }

    @SneakyThrows
    public long getBalance() {
        return new AccountBalanceQuery()
                .setAccountId(sdkClient.getExpandedOperatorAccountId().getAccountId())
                .execute(client)
                .hbars
                .toTinybars();
    }
}
