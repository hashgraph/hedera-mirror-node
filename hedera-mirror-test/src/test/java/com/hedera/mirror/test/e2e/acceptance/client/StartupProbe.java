package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.log4j.Log4j2;
import com.google.common.util.concurrent.Uninterruptibles;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.MirrorTransaction;
import com.hedera.mirror.test.e2e.acceptance.response.MirrorTransactionsResponse;

@Log4j2
/**
 * StartupProbe -- a helper class to validate a SDKClient before using it.
 */
public class StartupProbe {

    private final AcceptanceTestProperties acceptanceTestProperties;
    private final int maxRetries;
    private final MirrorNodeClient mirrorNodeClient;
    private final Instant startInstant;
    private final Instant timeoutInstant;

    private int retries = 1;
    private boolean validated;

    public StartupProbe(AcceptanceTestProperties acceptanceTestProperties, MirrorNodeClient mirrorNodeClient) {
        startInstant = Instant.now();
        this.acceptanceTestProperties = acceptanceTestProperties;
        this.mirrorNodeClient = mirrorNodeClient;
        maxRetries = acceptanceTestProperties.getMaxRetries();
        Duration startupTimeout = acceptanceTestProperties.getStartupTimeout();
        timeoutInstant = startInstant.plusNanos(startupTimeout.toNanos());
    }

    public void validateEnvironment() throws TimeoutException {
        // if we previously validated successfully, just immediately return.
        if (validated) {
            return;
        }
        // any of these can throw IllegalArgumentException; if it does, retries wouldn't help, so exit early (with that
        // exception) if it happens.
        Client client = Client.forName(acceptanceTestProperties.getNetwork().toString().toLowerCase());
        AccountId operator = AccountId.fromString(acceptanceTestProperties.getOperatorId());
        PrivateKey privateKey = PrivateKey.fromString(acceptanceTestProperties.getOperatorKey());
        client.setOperator(operator, privateKey);  // this account pays for (and signs) all transactions run here.

        // step 1: Create a new topic for the subsequent HCS submit message action.
        TransactionResponse transactionResponse = null;
        TransactionId transactionId = null;
        boolean createdTopic = false;
	for ( ; retries <= maxRetries; retries++) {
            try {
                transactionResponse = new TopicCreateTransaction().execute(client);
                transactionId = transactionResponse.transactionId;
                createdTopic = true;
                break;
            } catch (PrecheckStatusException pse) {
                log.warn("PrecheckStatusException on trying to create new topic, attempt #{}", retries, pse);
                throw new IllegalArgumentException(pse.getMessage());
            } catch (TimeoutException te) {
                log.warn("TimeoutException on trying to create new topic, attempt #{}", retries, te);
                if (Instant.now().compareTo(timeoutInstant) >= 0) {
                    throw new TimeoutException("Ran out of time on first HAPI call - creating new topic: "
                            + te.getMessage());
                }
            }
        }

        if (!createdTopic) {
            throw new IllegalArgumentException("StartupProbe (first HAPI call) : Could not create new topic after "
                    + maxRetries + " retries");
        }

        // step 2: Query for the receipt of that HCS submit transaction (until successful or time runs out)
        TransactionReceipt transactionReceipt = null;
        boolean gotReceipt = false;
	for ( retries = 1; retries <= maxRetries; retries++) {
            try {
                Duration remainingTime = Duration.between(Instant.now(), timeoutInstant);
                transactionReceipt = transactionResponse.getReceipt(client, remainingTime);
                if (transactionReceipt.status != Status.SUCCESS) {
                    log.warn("Calling transactionResponse.getReceipt resulted in status " + transactionReceipt.status
                             + ", not SUCCESS, on try #{}; retrying.", retries);
                    continue;
                }
                gotReceipt = true;
                break;
            } catch (PrecheckStatusException pse) {
                log.warn("PrecheckStatusException on trying to get submit transaction receipt, attempt #{}", retries, pse);
                throw new IllegalArgumentException("PrecheckStatusException on second HAPI call (getting transaction"
                        + " receipt): " + pse.getMessage());
            } catch (ReceiptStatusException rse) {
                log.warn("ReceiptStatusException on trying to get submit transaction receipt, attempt #{}", retries, rse);
                // try again
            } catch (TimeoutException te) {
                log.warn("TimeoutException on trying to get submit transaction receipt, attempt #{}", retries, te);
                if (Instant.now().compareTo(timeoutInstant) >= 0) {
                    throw new TimeoutException("Ran out of time on second HAPI call - getting submit transaction "
                            + " receipt:" + te.getMessage());
                }
            }
        }

        if (!gotReceipt) {
            throw new IllegalArgumentException("StartupProbe Could not retrieve receipt after " + maxRetries
                    + " retries");
        }

        // step 3: Query the mirror node for the transaction by ID (until successful or time runs out)
        String restTransactionId = transactionId.accountId.toString() + "-" + transactionId.validStart.getEpochSecond()
                + "-" + transactionId.validStart.getNano();
        MirrorTransactionsResponse mirrorTransactionsResponse = null;
        boolean gotRestApiResponse = false;
	// call to mirrorNodeClient will automatically retry (up to maxRetries times) on exceptions, so no need to loop
        try {
            mirrorTransactionsResponse = mirrorNodeClient.getTransactions(restTransactionId);
            for (MirrorTransaction mirrorTransaction : mirrorTransactionsResponse.getTransactions()) {
                if (mirrorTransaction.getResult().equalsIgnoreCase("SUCCESS")) {
                    gotRestApiResponse = true;
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("Exception on REST API call to get transaction by id (after #{} retries)", maxRetries, e);
            if (Instant.now().compareTo(timeoutInstant) >= 0) {
                throw new TimeoutException("Ran out of time on the REST API call - getting transaction by id: "
                        + e.getMessage());
            }
        }

        if (!gotRestApiResponse) {
            throw new IllegalArgumentException("StartupProbe Could not successfully make API call after " + maxRetries
                    + " retries");
        }

        // step 4: Query the mirror node gRPC API for a message on the topic created in step 2, until successful or
        //         time runs out
        TopicId topicId = transactionReceipt.topicId;
        AtomicBoolean messageReceived = new AtomicBoolean();

        boolean subscribedWithGrpc = false;
	for ( retries = 1; retries <= maxRetries; retries++) {
            try {
                new TopicMessageQuery()
                    .setTopicId(topicId)
                    .subscribe(client, resp -> {
                        messageReceived.set(true);
                    });
                subscribedWithGrpc = true;
            } catch (RuntimeException e) {
                log.warn("RuntimeException on try #{}/{} to subscribe using GRPC API call", retries, maxRetries, e);
                // try again
            }
        }

        if (!subscribedWithGrpc) {
            throw new IllegalArgumentException("StartupProbe Could not successfully make GRPC API subscription call "
                    + "after " + maxRetries + " retries");
        }

	for ( retries = 1; retries <= maxRetries; retries++) {
            try {
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
                if (messageReceived.get()) {  // we already had a previous success - no need to submit more trabsactions
                    break;
                }
                new TopicMessageSubmitTransaction()
                    .setTopicId(topicId)
                    .setMessage("Hello, HCS! " + retries)
                    .execute(client)
                    .getReceipt(client);

                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
                if (Instant.now().compareTo(timeoutInstant) >= 0) {
                    throw new TimeoutException("Ran out of time on second HAPI call - getting submit transaction "
                            + " receipt.");
                }
            } catch (PrecheckStatusException pse) {
                log.warn("PrecheckStatusException on GRPC API call to submit a transaction", pse);
                throw new IllegalArgumentException("PrecheckStatusException on GRPC call (submitting transaction):"
                        + pse.getMessage());
            } catch (ReceiptStatusException rse) {
                log.warn("ReceiptStatusException on trying to get receipt from GRPC API call", rse);
                // try again
            }
        }

        // make sure at least one message got through
        if (!messageReceived.get()) {
            throw new IllegalArgumentException("StartupProbe Could not successfully receive GRPC message after "
                    + maxRetries + " retries");
        }

        log.info("successful for all stages - HAPI, REST API, and gRPC API."); // ideally, the only line logged.
        validated = true;
    }

}
