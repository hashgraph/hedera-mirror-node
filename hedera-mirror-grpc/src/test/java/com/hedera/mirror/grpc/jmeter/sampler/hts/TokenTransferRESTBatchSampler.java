package com.hedera.mirror.grpc.jmeter.sampler.hts;

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

import com.hedera.mirror.grpc.jmeter.props.hts.TokenTransferGetRequest;

@Log4j2
public class TokenTransferRESTBatchSampler {
    private final TokenTransferGetRequest tokenTransferGetRequest;
    private final WebClient webClient;
    private Stopwatch stopwatch;
    private static final String REST_PATH = "/api/v1/transactions/";

    public TokenTransferRESTBatchSampler(TokenTransferGetRequest tokenTransferGetRequest) {
        this.tokenTransferGetRequest = tokenTransferGetRequest;
        webClient = WebClient.create(tokenTransferGetRequest.getRestBaseUrl());
    }

    public int retrieveTransaction() {
        stopwatch = Stopwatch.createStarted();
        List<String> transactions = Flux.fromIterable(tokenTransferGetRequest.getTransactionIds())
                //TODO this may be overkill.
                .parallel()
                .runOn(Schedulers.parallel())
                .flatMap(transactionId -> getTransaction(transactionId).onErrorResume(ex -> {
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
        return webClient.get().uri(REST_PATH + transactionId).retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.fixedDelay(tokenTransferGetRequest.getRestRetryMax(), Duration
                        .ofMillis(tokenTransferGetRequest.getRestRetryBackoffMs())));
    }
}
