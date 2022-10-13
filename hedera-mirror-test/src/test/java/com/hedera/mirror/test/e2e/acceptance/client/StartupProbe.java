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
import javax.inject.Named;
import lombok.CustomLog;
import lombok.SneakyThrows;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Uninterruptibles;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;

/**
 * StartupProbe -- a helper class to validate a SDKClient before using it.
 */
@CustomLog
@Named
public class StartupProbe {

    private final AcceptanceTestProperties acceptanceTestProperties;
    private final Duration startupTimeout;
    private final WebClient webClient;

    private boolean validated;

    public StartupProbe(AcceptanceTestProperties acceptanceTestProperties, WebClient webClient) {
        this.acceptanceTestProperties = acceptanceTestProperties;
        this.webClient = webClient;
        startupTimeout = acceptanceTestProperties.getStartupTimeout();
    }

    @SneakyThrows
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

        Stopwatch stopwatch = Stopwatch.createStarted();
        // step 1: Create a new topic for the subsequent HCS submit message action.
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .infiniteRetry()
                .notRetryOn(TimeoutException.class)
                .fixedBackoff(1000L)
                .build();
        
        TransactionResponse response = retryTemplate.execute(x -> new TopicCreateTransaction()
            .setGrpcDeadline(Duration.ofSeconds(10L))
            .setMaxAttempts(Integer.MAX_VALUE)
            .execute(client, startupTimeout.minus(stopwatch.elapsed())));
        TransactionId transactionId = response.transactionId;

        // step 2: Query for the receipt of that HCS submit transaction (until successful or time runs out)
        TransactionReceipt transactionReceipt = response.getReceipt(client,
                startupTimeout.minus(stopwatch.elapsed()));

        // step 3: Query the mirror node for the transaction by ID (until successful or time runs out)
        // Instead of using MirrorNodeClient.getTransactions(restTransactionId), we directly call webClient.
        // retry infinitely or until timeout (or success), not a maximum of properties.getMaxAttempts() times.
        var properties = acceptanceTestProperties.getRestPollingProperties();
        RetryBackoffSpec retrySpec = Retry.backoff(Integer.MAX_VALUE, properties.getMinBackoff())
                .maxBackoff(properties.getMaxBackoff())
                .filter(this::shouldRetryRestCall);

        String restTransactionId = transactionId.accountId.toString() + "-" + transactionId.validStart.getEpochSecond()
                + "-" + transactionId.validStart.getNano();

        webClient.get()
                .uri("/transactions/", restTransactionId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(retrySpec)
                .doOnError(x -> log.error("Endpoint failed, returning: {}", x.getMessage()))
                .timeout(startupTimeout.minus(stopwatch.elapsed()))
                .block();

        // step 4: Query the mirror node gRPC API for a message on the topic created in step 2, until successful or
        //         time runs out
        TopicId topicId = transactionReceipt.topicId;
        AtomicBoolean messageReceived = new AtomicBoolean();

        retryTemplate.execute(x -> new TopicMessageQuery()
                .setTopicId(topicId)
                .subscribe(client, resp -> {
                    messageReceived.set(true);
                }));

        retryTemplate.execute(x -> {
            int counter = 0;
            while (!(messageReceived.get()) && startupTimeout.minus(stopwatch.elapsed()).toMillis() > 0) {
                new TopicMessageSubmitTransaction()
                    .setTopicId(topicId)
                    .setMessage("Hello, HCS! " + ++counter)
                    .execute(client, startupTimeout.minus(stopwatch.elapsed()))
                    .getReceipt(client);
                Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
            }
            return null;
        });

        if (messageReceived.get()) {
            log.info("Startup probe successful.");
            validated = true;
        } else {
            throw new TimeoutException("Timer expired while trying to receive topic messages.");
        }
    }

    // used by the retrySpec created in validateEnvironment(); this routine copied from MirrorNodeClient.java.
    private boolean shouldRetryRestCall(Throwable t) {
        return acceptanceTestProperties.getRestPollingProperties().getRetryableExceptions()
                .stream()
                .anyMatch(ex -> ex.isInstance(t) || ex.isInstance(Throwables.getRootCause(t)));
    }

}
