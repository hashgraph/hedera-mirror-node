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

import java.util.List;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionBuilder;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionList;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.account.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.crypto.PrivateKey;
import com.hedera.mirror.test.e2e.acceptance.response.NetworkTransactionResponse;

@Log4j2
@Data
public abstract class AbstractNetworkClient {
    protected final SDKClient sdkClient;
    protected final Client client;

    public AbstractNetworkClient(SDKClient sdkClient) {
        this.sdkClient = sdkClient;
        client = sdkClient.getClient();
    }

    public TransactionId executeTransaction(TransactionBuilder transactionBuilder, PrivateKey key) throws HederaStatusException {

        Transaction builtTransaction = (Transaction) transactionBuilder.build(client);

        if (key != null) {
            builtTransaction = builtTransaction.sign(key);
        }

        TransactionId transactionId = builtTransaction.execute(client);
        log.debug("Executed transaction {}.", transactionId);

        return transactionId;
    }

    public NetworkTransactionResponse executeTransactionAndRetrieveReceipt(TransactionBuilder transactionBuilder,
                                                                           PrivateKey key) throws HederaStatusException {
        long startBalance = getBalance();
        TransactionId transactionId = executeTransaction(transactionBuilder, key);
        TransactionReceipt transactionReceipt = transactionId.getReceipt(client);
        log.trace("Executed transaction {} cost {} tℏ", transactionId, startBalance - getBalance());
        return new NetworkTransactionResponse(transactionId, transactionReceipt);
    }

    public List<TransactionId> executeTransactionList(TransactionBuilder transactionBuilder, PrivateKey key) throws HederaStatusException {

        TransactionList transactionList = (TransactionList) transactionBuilder.build(client);

        if (key != null) {
            transactionList = transactionList.sign(key);
        }

        List<TransactionId> transactionIdList = transactionList.executeAll(client);
        if (transactionIdList.size() == 1) {
            log.debug("Executed transaction {}.", transactionIdList.get(0));
        } else {
            log.debug("Executed {} transactions.", transactionIdList.size());
        }

        return transactionIdList;
    }

    public long getBalance() throws HederaStatusException {
        return new AccountBalanceQuery()
                .setAccountId(sdkClient.getOperatorId())
                .execute(client).asTinybar();
    }
}
