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
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
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

    public TransactionId executeTransaction(Transaction transaction, KeyList keyList) throws Exception {

        // set max retries on sdk
        transaction.setMaxAttempts(sdkClient.getAcceptanceTestProperties().getSdkProperties().getMaxAttempts());

        if (keyList != null) {
            transaction.freezeWith(client); // Signing requires transaction to be frozen
            for (Key k : keyList) {
                transaction = transaction.sign((PrivateKey) k);
            }
            log.debug("{} additional signatures added to transaction", keyList.size());
        }

        Transaction finalTransaction = transaction;
        TransactionResponse transactionResponse = (TransactionResponse) retryTemplate.execute(x ->
                finalTransaction.execute(client));
        TransactionId transactionId = transactionResponse.transactionId;
        log.debug("Executed transaction {} with {} signatures.", transactionId, keyList == null ? 0 : keyList.size());

        return transactionId;
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction transaction,
                                                                           KeyList keyList) throws Exception {
        long startBalance = getBalance();
        TransactionId transactionId = executeTransaction(transaction, keyList);
        TransactionReceipt transactionReceipt = getTransactionReceipt(transactionId);
        log.trace("Executed transaction {} cost {} tℏ", transactionId, startBalance - getBalance());
        return new NetworkTransactionResponse(transactionId, transactionReceipt);
    }

    public TransactionReceipt getTransactionReceipt(TransactionId transactionId) throws Exception {
        return retryTemplate.execute(x -> transactionId.getReceipt(client));
    }

    public TransactionRecord getTransactionRecord(TransactionId transactionId) throws Exception {
        return retryTemplate.execute(x -> transactionId.getRecord(client));
    }

    public long getBalance() throws TimeoutException, PrecheckStatusException {
        return new AccountBalanceQuery()
                .setAccountId(sdkClient.getExpandedOperatorAccountId().getAccountId())
                .execute(client)
                .hbars
                .toTinybars();
    }
}
