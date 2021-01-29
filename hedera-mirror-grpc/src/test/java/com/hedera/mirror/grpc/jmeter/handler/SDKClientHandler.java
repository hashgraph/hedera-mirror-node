package com.hedera.mirror.grpc.jmeter.handler;

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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.HederaStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.TransferTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicCreateTransaction;
import com.hedera.hashgraph.sdk.consensus.ConsensusTopicId;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;
import com.hedera.hashgraph.sdk.token.TokenId;
import com.hedera.mirror.grpc.jmeter.props.NodeInfo;

@Log4j2
@Value
public class SDKClientHandler {
    protected final Client client;
    private final NodeInfo nodeInfo;
    private final AccountId operatorId;
    @Getter(AccessLevel.NONE)
    private final Ed25519PrivateKey operatorPrivateKey;

    public SDKClientHandler(String nodeParts, AccountId operatorId, Ed25519PrivateKey operatorPrivateKey) {
        nodeInfo = new NodeInfo(nodeParts);
        this.operatorId = operatorId;
        this.operatorPrivateKey = operatorPrivateKey;

        client = new Client(Map.of(nodeInfo.getNodeId(), nodeInfo.getNodeAddress()));
        client.setOperator(operatorId, operatorPrivateKey);

        log.trace("Created client for {}", nodeInfo);
    }

    public void close() throws InterruptedException {
        log.debug("Closing SDK client, waits up to 10 s for valid close");

        try {
            if (client != null) {
                client.close(5, TimeUnit.SECONDS);
            }
        } catch (TimeoutException tex) {
            log.debug("Exception on client close: {}", tex.getMessage());
        }
    }

    public ConsensusTopicId createTopic() throws HederaStatusException {

        ConsensusTopicCreateTransaction consensusTopicCreateTransaction = new ConsensusTopicCreateTransaction()
                .setAdminKey(operatorPrivateKey.publicKey)
                .setAutoRenewAccountId(operatorId)
                .setMaxTransactionFee(1_000_000_000)
                .setTopicMemo("HCS Topic_" + Instant.now());

        return createTopic(consensusTopicCreateTransaction);
    }

    public ConsensusTopicId createTopic(ConsensusTopicCreateTransaction consensusTopicCreateTransaction) throws HederaStatusException {
        TransactionReceipt transactionReceipt = consensusTopicCreateTransaction
                .execute(client)
                .getReceipt(client);

        ConsensusTopicId topicId = transactionReceipt.getConsensusTopicId();
        log.info("Created new topic {}, with TransactionReceipt : {}", topicId, transactionReceipt);

        return topicId;
    }

    public List<TransactionId> submitTopicMessage(ConsensusTopicId topicId, byte[] message) throws HederaStatusException {
        ConsensusMessageSubmitTransaction consensusMessageSubmitTransaction = new ConsensusMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage(message);

        return submitTopicMessage(consensusMessageSubmitTransaction);
    }

    public List<TransactionId> submitTopicMessage(ConsensusMessageSubmitTransaction consensusMessageSubmitTransaction) throws HederaStatusException {
        return consensusMessageSubmitTransaction.executeAll(client);
    }

    public TransactionId submitCryptoTransfer(AccountId operatorId, AccountId recipientId, int amount) throws HederaStatusException {
        TransactionId transactionId = new TransferTransaction()
                .addHbarTransfer(operatorId, Math.negateExact(amount))
                .addHbarTransfer(recipientId, amount)
                .setTransactionMemo("transfer test")
                .execute(client);

        return transactionId;
    }

    public TransactionId submitTokenTransfer(TokenId tokenId, AccountId operatorId, AccountId recipientId,
                                             long transferAmount) throws HederaStatusException {
        TransactionId transactionId = new TransferTransaction()
                .addTokenTransfer(tokenId, operatorId, Math.negateExact(transferAmount))
                .addTokenTransfer(tokenId, recipientId, transferAmount)
                .setMaxTransactionFee(10_000_000L)
                .setTransactionMemo("Token Transfer_" + Instant.now())
                .execute(client);

        return transactionId;
    }

    public int getValidTransactionsCount(List<TransactionId> transactionIds) {
        log.debug("Verify Transactions {}", transactionIds.size());
        AtomicInteger counter = new AtomicInteger(0);
        transactionIds.forEach(x -> {
            TransactionReceipt receipt = null;
            try {
                receipt = x.getReceipt(client);
            } catch (HederaStatusException e) {
                log.debug("Error pulling {} receipt {}", x, e.getMessage());
            }
            if (receipt.status == Status.Success) {
                counter.incrementAndGet();
            } else {
                log.warn("Transaction {} had an unexpected status of {}", x, receipt.status);
            }
        });

        log.debug("{} out of {} transactions returned a Success status", counter.get(), transactionIds.size());
        return counter.get();
    }

    public List<TransactionId> getValidTransactions(List<TransactionId> transactionIds) {
        log.debug("Verify Transactions {}", transactionIds.size());
        List<TransactionId> validTransactions = new ArrayList<>();
        transactionIds.forEach(x -> {
            TransactionReceipt receipt = null;
            try {
                receipt = x.getReceipt(client);
            } catch (HederaStatusException e) {
                log.debug("Error pulling {} receipt {}", x, e.getMessage());
            }
            if (receipt.status == Status.Success) {
                validTransactions.add(x);
            } else {
                log.warn("Transaction {} had an unexpected status of {}", x, receipt.status);
            }
        });

        log.debug("{} out of {} transactions returned a Success status", validTransactions.size(), transactionIds
                .size());
        return validTransactions;
    }
}
