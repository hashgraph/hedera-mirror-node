package com.hedera.mirror.test.e2e.acceptance.client;

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
import java.util.function.Supplier;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.retry.support.RetryTemplate;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Key;
import com.hedera.hashgraph.sdk.KeyList;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.Query;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionReceiptQuery;
import com.hedera.hashgraph.sdk.TransactionRecord;
import com.hedera.hashgraph.sdk.TransactionRecordQuery;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Data
public abstract class AbstractNetworkClient {

    private static final int MEMO_BYTES_MAX_LENGTH = 100;

    protected final Client client;
    protected final Logger log = LogManager.getLogger(getClass());
    protected final SDKClient sdkClient;
    protected final RetryTemplate retryTemplate;

    public AbstractNetworkClient(SDKClient sdkClient, RetryTemplate retryTemplate) {
        this.sdkClient = sdkClient;
        this.client = sdkClient.getClient();
        this.retryTemplate = retryTemplate;
    }

    @SneakyThrows
    public <O, T extends Query<O, T>> O executeQuery(Supplier<Query<O, T>> querySupplier) {
        var grpcDeadline = sdkClient.getAcceptanceTestProperties().getSdkProperties().getGrpcDeadline();
        return retryTemplate.execute(x -> querySupplier.get().setGrpcDeadline(grpcDeadline).execute(client));
    }

    @SneakyThrows
    public TransactionId executeTransaction(Transaction<?> transaction, KeyList keyList, ExpandedAccountId payer) {
        int numSignatures = 0;

        if (payer != null) {
            transaction.setTransactionId(TransactionId.generate(payer.getAccountId()));

            transaction.freezeWith(client);
            transaction.sign(payer.getPrivateKey());
            numSignatures++;
        }

        if (keyList != null) {
            transaction.freezeWith(client); // Signing requires transaction to be frozen
            for (Key k : keyList) {
                transaction.sign((PrivateKey) k);
            }
            log.debug("{} additional signatures added to transaction", keyList.size());
            numSignatures += keyList.size();
        }

        // Set properties from config
        var sdkProperties = sdkClient.getAcceptanceTestProperties().getSdkProperties();
        transaction.setGrpcDeadline(sdkProperties.getGrpcDeadline());
        transaction.setMaxAttempts(sdkProperties.getMaxAttempts());

        var transactionResponse = retryTemplate.execute(x -> transaction.execute(client));
        var transactionId = transactionResponse.transactionId;
        log.debug("Executed transaction {} with {} signatures.", transactionId, numSignatures);

        return transactionId;
    }

    public TransactionId executeTransaction(Transaction<?> transaction, KeyList keyList) {
        return executeTransaction(transaction, keyList, null);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction<?> transaction, KeyList keyList,
                                                                           ExpandedAccountId payer) {
        long startBalance = log.isTraceEnabled() ? getBalance() : 0L;
        var transactionId = executeTransaction(transaction, keyList, payer);
        var transactionReceipt = getTransactionReceipt(transactionId);

        if (log.isTraceEnabled()) {
            log.trace("Executed transaction {} cost {} tℏ", transactionId, startBalance - getBalance());
        }

        return new NetworkTransactionResponse(transactionId, transactionReceipt);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction<?> transaction, KeyList keyList) {
        return executeTransactionAndRetrieveReceipt(transaction, keyList, null);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction<?> transaction,
                                                                           ExpandedAccountId payer) {
        return executeTransactionAndRetrieveReceipt(transaction, null, payer);
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(Transaction<?> transaction) {
        return executeTransactionAndRetrieveReceipt(transaction, null, null);
    }

    public TransactionReceipt getTransactionReceipt(TransactionId transactionId) {
        var query = new TransactionReceiptQuery().setTransactionId(transactionId);
        var receipt = executeQuery(() -> query);

        if (log.isDebugEnabled()) {
            log.debug("Transaction receipt: {}", receipt);
        }

        if (receipt.status != Status.SUCCESS) {
            throw new NetworkException("Transaction was unsuccessful: " + receipt);
        }

        return receipt;
    }

    @SneakyThrows
    public TransactionRecord getTransactionRecord(TransactionId transactionId) {
        var grpcDeadline = sdkClient.getAcceptanceTestProperties().getSdkProperties().getGrpcDeadline();
        return retryTemplate.execute(x -> {
            var receipt = new TransactionReceiptQuery()
                    .setTransactionId(transactionId)
                    .setGrpcDeadline(grpcDeadline)
                    .execute(client);
            if (receipt.status != Status.SUCCESS) {
                throw new RuntimeException(String.format("Transaction %s is unsuccessful: %s", transactionId,
                        receipt.status));
            }

            return new TransactionRecordQuery()
                    .setTransactionId(transactionId)
                    .setGrpcDeadline(grpcDeadline)
                    .execute(client);
        });
    }

    public long getBalance() {
        return getBalance(sdkClient.getExpandedOperatorAccountId());
    }

    public long getBalance(ExpandedAccountId accountId) {
        // AccountBalanceQuery is free
        var query = new AccountBalanceQuery().setAccountId(accountId.getAccountId());
        var balance = executeQuery(() -> query).hbars;
        log.info("{} balance is {}", accountId, balance);
        return balance.toTinybars();
    }

    protected String getMemo(String message) {
        String memo = String.format("Mirror Node acceptance test: %s %s", Instant.now(), message);
        // Memos are capped at 100 bytes
        return StringUtils.truncate(memo, MEMO_BYTES_MAX_LENGTH);
    }
}
