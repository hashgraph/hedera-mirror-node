/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.test.e2e.acceptance.client;

import com.google.common.base.Stopwatch;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Query;
import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceiptQuery;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import jakarta.inject.Named;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

/**
 * StartupProbe -- a helper class to validate a SDKClient before using it.
 */
@CustomLog
@Named
@RequiredArgsConstructor
public class StartupProbe {

    private final AcceptanceTestProperties acceptanceTestProperties;
    private final WebClient webClient;

    public void validateEnvironment(Client client) {
        var startupTimeout = acceptanceTestProperties.getStartupTimeout();
        var stopwatch = Stopwatch.createStarted();

        if (startupTimeout.equals(Duration.ZERO)) {
            log.warn("Startup probe disabled");
            return;
        }

        log.info("Creating a topic to confirm node connectivity");
        var transactionId = executeTransaction(client, stopwatch, () -> new TopicCreateTransaction()).transactionId;
        var receiptQuery = new TransactionReceiptQuery().setTransactionId(transactionId);
        var topicId = executeQuery(client, stopwatch, () -> receiptQuery).topicId;
        log.info("Created topic {} successfully", topicId);

        callRestEndpoint(stopwatch, transactionId);
        long startTime;

        // Submit a topic message and ensure it's seen by mirror node within 30s to ensure the importer is caught up
        do {
            startTime = System.currentTimeMillis();
            submitMessage(client, stopwatch, topicId);
        } while (System.currentTimeMillis() - startTime > 30_000);

        log.info("Startup probe successful");
    }

    @SneakyThrows
    private void submitMessage(Client client, Stopwatch stopwatch, TopicId topicId) {
        var messageLatch = new CountDownLatch(1);
        var startupTimeout = acceptanceTestProperties.getStartupTimeout();
        SubscriptionHandle subscription = null;

        try {
            log.info("Subscribing to topic {}", topicId);
            var retry = retryOperations(stopwatch);
            subscription = retry.execute(x -> new TopicMessageQuery()
                    .setTopicId(topicId)
                    .setMaxAttempts(Integer.MAX_VALUE)
                    .setRetryHandler(t -> {
                        log.info("Retrying exception: {}", t.getMessage());
                        return true;
                    })
                    .setStartTime(Instant.EPOCH)
                    .subscribe(client, resp -> messageLatch.countDown()));

            log.info("Submitting a message to the network");
            var transactionIdMessage = executeTransaction(client, stopwatch, () -> new TopicMessageSubmitTransaction()
                            .setTopicId(topicId)
                            .setMessage("Mirror Node acceptance test"))
                    .transactionId;

            executeQuery(client, stopwatch, () -> new TransactionReceiptQuery().setTransactionId(transactionIdMessage));
            log.info("Waiting for the mirror node to publish the topic message");

            if (!messageLatch.await(startupTimeout.minus(stopwatch.elapsed()).toNanos(), TimeUnit.NANOSECONDS)) {
                throw new TimeoutException("Timer expired while waiting on message latch");
            }

            log.info("Received the topic message");
        } finally {
            if (subscription != null) {
                subscription.unsubscribe();
            }
        }
    }

    @SneakyThrows
    private TransactionResponse executeTransaction(
            Client client, Stopwatch stopwatch, Supplier<Transaction<?>> transaction) {
        var retry = retryOperations(stopwatch);
        return retry.execute(
                r -> transaction.get().setMaxAttempts(Integer.MAX_VALUE).execute(client, Duration.ofSeconds(30L)));
    }

    @SneakyThrows
    private <T> T executeQuery(Client client, Stopwatch stopwatch, Supplier<Query<T, ?>> transaction) {
        var retry = retryOperations(stopwatch);
        return retry.execute(
                r -> transaction.get().setMaxAttempts(Integer.MAX_VALUE).execute(client, Duration.ofSeconds(30L)));
    }

    private void callRestEndpoint(Stopwatch stopwatch, TransactionId transactionId) {
        var startupTimeout = acceptanceTestProperties.getStartupTimeout();
        var properties = acceptanceTestProperties.getRestPollingProperties();
        var retrySpec = Retry.backoff(Integer.MAX_VALUE, properties.getMinBackoff())
                .maxBackoff(properties.getMaxBackoff())
                .filter(properties::shouldRetry);

        var restTransactionId = transactionId.accountId + "-" + transactionId.validStart.getEpochSecond() + "-"
                + transactionId.validStart.getNano();

        String uri = "/transactions/" + restTransactionId;
        webClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(retrySpec)
                .doOnError(x -> log.warn("Endpoint {} failed, returning: {}", uri, x.getMessage()))
                .timeout(startupTimeout.minus(stopwatch.elapsed()))
                .block();
    }

    private RetryOperations retryOperations(Stopwatch stopwatch) {
        return RetryTemplate.builder()
                .exponentialBackoff(1000, 2.0, 10000)
                .withTimeout(acceptanceTestProperties.getStartupTimeout().minus(stopwatch.elapsed()))
                .withListener(new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(RetryContext r, RetryCallback<T, E> c, Throwable t) {
                        log.warn("Retry attempt #{} with error: {}", r.getRetryCount(), t.getMessage());
                    }
                })
                .build();
    }
}
