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

package com.hedera.mirror.monitor.publish;

import static com.hedera.hashgraph.sdk.Status.SUCCESS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.NodeProperties;
import com.hedera.mirror.monitor.subscribe.rest.RestApiClient;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Named;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@CustomLog
@Named
@RequiredArgsConstructor
public class NodeSupplier {

    private final MonitorProperties monitorProperties;
    private final RestApiClient restApiClient;

    private final CopyOnWriteArrayList<NodeProperties> nodes = new CopyOnWriteArrayList<>();
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        var validationProperties = monitorProperties.getNodeValidation();
        int parallelism = validationProperties.getMaxThreads();
        long retryBackoff = validationProperties.getRetryBackoff().toMillis();

        var scheduler = Schedulers.newParallel("validator", parallelism + 1);
        Flux.interval(Duration.ZERO, validationProperties.getFrequency(), scheduler)
                .flatMap(i -> refresh()
                        .parallel(parallelism)
                        .runOn(scheduler)
                        .map(this::validateNode)
                        .sequential()
                        .collectList()
                        .doOnNext(valid -> log.info(
                                "{} of {} nodes are functional",
                                valid.stream().filter(v -> v).count(),
                                valid.size()))
                        .repeatWhen(retry -> retry.filter(l -> nodes.isEmpty())
                                .doOnNext(delay -> log.info("Retrying in {} ms", retryBackoff))
                                .flatMap(delay -> Mono.delay(Duration.ofMillis(delay)))))
                .doOnSubscribe(s -> log.info("Starting node validation"))
                .doOnError(t -> log.error("Exception validating nodes: ", t))
                .onErrorResume(t -> Mono.empty())
                .subscribe();
    }

    public NodeProperties get() {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No valid nodes available");
        }

        int nodeIndex = secureRandom.nextInt(nodes.size());
        return nodes.get(nodeIndex);
    }

    public synchronized Flux<NodeProperties> refresh() {
        boolean empty = nodes.isEmpty();
        Retry retrySpec = Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1L))
                .maxBackoff(Duration.ofSeconds(8L))
                .scheduler(Schedulers.newSingle("nodes"))
                .doBeforeRetry(r -> log.warn(
                        "Retry attempt #{} after failure: {}",
                        r.totalRetries() + 1,
                        r.failure().getMessage()));

        return Flux.fromIterable(monitorProperties.getNodes())
                .doOnSubscribe(s -> log.info("Refreshing node list"))
                .switchIfEmpty(Flux.defer(this::getAddressBook))
                .switchIfEmpty(Flux.fromIterable(monitorProperties.getNetwork().getNodes()))
                .retryWhen(retrySpec)
                .switchIfEmpty(Flux.error(new IllegalArgumentException("Nodes must not be empty")))
                .doOnNext(n -> {
                    if (empty) {
                        nodes.addIfAbsent(n);
                    }
                }); // Populate on startup before validation
    }

    private Flux<NodeProperties> getAddressBook() {
        if (!monitorProperties.getNodeValidation().isRetrieveAddressBook()) {
            return Flux.empty();
        }

        var count = new AtomicInteger(0);

        return Flux.defer(restApiClient::getNodes)
                .filter(n -> !CollectionUtils.isEmpty(n.getServiceEndpoints()))
                .map(n -> new NodeProperties(
                        n.getNodeAccountId(), n.getServiceEndpoints().get(0).getIpAddressV4()))
                .doOnNext(n -> count.incrementAndGet())
                .doOnComplete(() -> log.info("Retrieved {} nodes from address book", count));
    }

    private Client toClient(Map<String, AccountId> nodes) {
        AccountId operatorId =
                AccountId.fromString(monitorProperties.getOperator().getAccountId());
        PrivateKey operatorPrivateKey =
                PrivateKey.fromString(monitorProperties.getOperator().getPrivateKey());
        var validationProperties = monitorProperties.getNodeValidation();

        Client client = Client.forNetwork(nodes);
        client.setMaxAttempts(validationProperties.getMaxAttempts());
        client.setMaxBackoff(validationProperties.getMaxBackoff());
        client.setMinBackoff(validationProperties.getMinBackoff());
        client.setOperator(operatorId, operatorPrivateKey);
        client.setRequestTimeout(validationProperties.getRequestTimeout());
        return client;
    }

    @VisibleForTesting
    boolean validateNode(NodeProperties node) {
        if (!monitorProperties.getNodeValidation().isEnabled()) {
            nodes.addIfAbsent(node);
            log.info("Adding node {} without validation", node.getAccountId());
            return true;
        }

        log.info("Validating node {}", node);
        Hbar hbar = Hbar.fromTinybars(1L);
        AccountId nodeAccountId = AccountId.fromString(node.getAccountId());

        try (Client client = toClient(Map.of(node.getEndpoint(), nodeAccountId))) {
            Status receiptStatus = new TransferTransaction()
                    .addHbarTransfer(nodeAccountId, hbar)
                    .addHbarTransfer(client.getOperatorAccountId(), hbar.negated())
                    .setNodeAccountIds(node.getAccountIds())
                    .execute(client)
                    .getReceipt(client)
                    .status;

            if (receiptStatus == SUCCESS) {
                log.info("Validated node {} successfully", nodeAccountId);
                nodes.addIfAbsent(node);
                return true;
            }

            log.warn("Unable to validate node {}: invalid status code {}", node, receiptStatus);
        } catch (TimeoutException e) {
            log.warn("Unable to validate node {}: Timed out", node);
        } catch (Exception e) {
            log.warn("Unable to validate node {}: ", node, e);
        }

        nodes.remove(node);
        return false;
    }
}
