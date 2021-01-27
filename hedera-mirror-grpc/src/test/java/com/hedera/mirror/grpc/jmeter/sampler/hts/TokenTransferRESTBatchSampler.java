package com.hedera.mirror.grpc.jmeter.sampler.hts;

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

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import com.hedera.mirror.grpc.jmeter.props.hts.RESTGetByIdsRequest;

@Log4j2
public class TokenTransferRESTBatchSampler {
    private final RESTGetByIdsRequest restGetByIdsRequest;
    private final WebClient webClient;
    private Stopwatch stopwatch;
    private static final String REST_PATH = "/api/v1/transactions/{id}";

    public TokenTransferRESTBatchSampler(RESTGetByIdsRequest restGetByIdsRequest) {
        this.restGetByIdsRequest = restGetByIdsRequest;
        webClient = WebClient.create(restGetByIdsRequest.getRestBaseUrl());
    }

    public int retrieveTransaction() {
        stopwatch = Stopwatch.createStarted();
        List<String> transactions = Flux.fromIterable(restGetByIdsRequest.getIds())
                .parallel()
                .runOn(Schedulers.parallel())
                .flatMap(transactionId -> getTransaction(transactionId)
                        //If errors bubble up, log and move on.
                        .onErrorResume(ex -> {
                            log.info("Failed to retrieve transaction {}: {}", transactionId, ex);
                            return Mono.empty();
                        }))
                .sequential()
                .collectList()
                .block();

        log.info("Retrieved {} transactions in {} ms", transactions.size(), stopwatch.elapsed(TimeUnit.MILLISECONDS));
        return transactions.size();
    }

    private Mono<String> getTransaction(String transactionId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path(REST_PATH).build(transactionId))
                .retrieve()
                .bodyToMono(String.class)
                //IF a 404 (or other error) is retrieved, keep trying periodically
                .retryWhen(Retry.fixedDelay(restGetByIdsRequest.getRestRetryMax(), Duration
                        .ofMillis(restGetByIdsRequest.getRestRetryBackoffMs())));
    }
}
