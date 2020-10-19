package com.hedera.mirror.grpc.jmeter.sampler.hts;

import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Log4j2
public class HTSRESTSampler {
    private final WebClient webClient;
    private Stopwatch stopwatch;

    public HTSRESTSampler(String restBaseUrl) {
        webClient = WebClient.create(restBaseUrl);
    }

    public int retrieveTransaction(List<String> formattedTransactionIds) {
        stopwatch = Stopwatch.createStarted();
        List<String> transactions = Flux.fromIterable(formattedTransactionIds)
//                .parallel()
//                .runOn(Schedulers.parallel())
                //TODO the first GET is much longer because it's doing the time since subscription, I have this to
                // reset the timer, probably a better way.
                .elapsed()
                .flatMap(transactionId -> getTransaction(transactionId.getT2()).onErrorResume(ex -> {
                    log.info("Failed to retrieve transaction {}", transactionId);
                    return Mono.empty();
                }))
//                .elapsed()
//                .doOnNext(tuple -> log.info("It took me {} ms to retrieve {}", tuple.getT1(), tuple.getT2()))
////                .elap
//                .doOnNext(tuple -> {
//                    publishTokenTransferLatencyStats.addValue(tuple.getT1());
//                })
                .collectList()
                .block();

        log.info("Total time for REST validation: {}", stopwatch.elapsed(TimeUnit.MILLISECONDS));
//        printPublishStats();
        return transactions.size();
    }

    private Mono<String> getTransaction(String transactionId) {
        return webClient.get().uri("/api/v1/transactions/" + transactionId).retrieve()
                .bodyToMono(String.class)
                .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(1)));
    }
}
