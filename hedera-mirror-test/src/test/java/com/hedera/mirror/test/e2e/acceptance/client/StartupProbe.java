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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.SneakyThrows;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Uninterruptibles;
import org.springframework.http.MediaType;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.SubscriptionHandle;
import com.hedera.hashgraph.sdk.TopicCreateTransaction;
import com.hedera.hashgraph.sdk.TopicId;
import com.hedera.hashgraph.sdk.TopicMessageQuery;
import com.hedera.hashgraph.sdk.TopicMessageSubmitTransaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionReceipt;
import com.hedera.hashgraph.sdk.TransactionReceiptQuery;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.NodeProperties;

import static com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties.HederaNetwork.OTHER;

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
    public void validateEnvironment(Client client) throws TimeoutException {
        // if we previously validated successfully, just immediately return.
        if (validated) {
            return;
        }
        // Any of these (before the "try") can throw IllegalArgumentException; if it does, retries wouldn't help,
        // so exit early (with that exception) if it does happen.
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
                .setGrpcDeadline(acceptanceTestProperties.getSdkProperties().getGrpcDeadline())
                .setMaxAttempts(Integer.MAX_VALUE)
                .execute(client, startupTimeout.minus(stopwatch.elapsed())));
        TransactionId transactionId = response.transactionId;

        // step 2: Query for the receipt of that HCS submit transaction (until successful or time runs out)
        TransactionReceipt transactionReceipt = retryTemplate.execute(x -> new TransactionReceiptQuery()
                .setTransactionId(transactionId)
                .setGrpcDeadline(acceptanceTestProperties.getSdkProperties().getGrpcDeadline())
                .setMaxAttempts(Integer.MAX_VALUE)
                .execute(client, startupTimeout.minus(stopwatch.elapsed())));

        // step 3: Query the mirror node for the transaction by ID (until successful or time runs out)
        // Instead of using MirrorNodeClient.getTransactions(restTransactionId), we directly call webClient.
        // retry infinitely or until timeout (or success), not a maximum of properties.getMaxAttempts() times.
        var properties = acceptanceTestProperties.getRestPollingProperties();
        RetryBackoffSpec retrySpec = Retry.backoff(Integer.MAX_VALUE, properties.getMinBackoff())
                .maxBackoff(properties.getMaxBackoff())
                .filter(properties::shouldRetry);

        String restTransactionId = transactionId.accountId.toString() + "-"
                + transactionId.validStart.getEpochSecond() + "-" + transactionId.validStart.getNano();

        String uri = "/transactions/" + restTransactionId;
        webClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .retryWhen(retrySpec)
                .doOnError(x -> log.warn("Endpoint {} failed, returning: {}", uri, x.getMessage()))
                .timeout(startupTimeout.minus(stopwatch.elapsed()))
                .block();

        // step 4: Query the mirror node gRPC API for a message on the topic created in step 2, until successful or
        //         time runs out
        TopicId topicId = transactionReceipt.topicId;
        CountDownLatch messageLatch = new CountDownLatch(1);

        SubscriptionHandle subscription = retryTemplate.execute(x -> new TopicMessageQuery()
                .setMaxAttempts(Integer.MAX_VALUE)
                .setRetryHandler(t -> true)
                .setStartTime(Instant.EPOCH)
                .setTopicId(topicId)
                .subscribe(client, resp -> {
                    messageLatch.countDown();
                }));

        TransactionResponse secondResponse = retryTemplate.execute(x -> new TopicMessageSubmitTransaction()
                .setTopicId(topicId)
                .setMessage("Hello, HCS!")
                .execute(client, startupTimeout.minus(stopwatch.elapsed())));
        TransactionId secondTransactionId = secondResponse.transactionId;

        retryTemplate.execute(x -> new TransactionReceiptQuery()
                .setTransactionId(secondTransactionId)
                .setGrpcDeadline(acceptanceTestProperties.getSdkProperties().getGrpcDeadline())
                .setMaxAttempts(Integer.MAX_VALUE)
                .execute(client, startupTimeout.minus(stopwatch.elapsed())));

        if (messageLatch.await(startupTimeout.minus(stopwatch.elapsed()).toNanos(), TimeUnit.NANOSECONDS)) {
            // clean up - cancel the subscription from step 4a
            subscription.unsubscribe();
            log.info("Startup probe successful.");
            validated = true;
        } else {
            throw new TimeoutException("Timer expired while waiting on message latch.");
        }
    }

}
