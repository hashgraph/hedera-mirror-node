package com.hedera.mirror.monitor.publish;

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

import static com.hedera.hashgraph.sdk.Status.SUCCESS;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HbarUnit;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.Status;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionRecordQuery;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.hashgraph.sdk.TransferTransaction;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.NodeProperties;
import com.hedera.mirror.monitor.NodeValidationProperties;

@Log4j2
@Named
@RequiredArgsConstructor
public class TransactionPublisher implements AutoCloseable {

    private final MonitorProperties monitorProperties;
    private final PublishProperties publishProperties;
    private final CopyOnWriteArrayList<NodeProperties> nodes = new CopyOnWriteArrayList<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final AtomicReference<Disposable> nodeValidator = new AtomicReference<>();
    private final AtomicReference<Client> validationClient = new AtomicReference<>();
    private final Flux<Client> clients = Flux.defer(this::getClients).cache();

    @Override
    public void close() {
        if (nodeValidator.get() != null) {
            nodeValidator.get().dispose();
        }

        if (publishProperties.isEnabled()) {
            log.warn("Closing {} clients", publishProperties.getClients());
            clients.subscribe(client -> {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore
                }
            });

            Client client = validationClient.get();
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    public Mono<PublishResponse> publish(PublishRequest request) {
        if (!publishProperties.isEnabled()) {
            return Mono.empty();
        }

        log.trace("Publishing: {}", request);
        int clientIndex = secureRandom.nextInt(publishProperties.getClients());
        PublishScenario scenario = request.getScenario();
        PublishScenarioProperties properties = scenario.getProperties();

        return clients.elementAt(clientIndex)
                .flatMap(client -> getTransactionResponse(request, client)
                        .flatMap(r -> processTransactionResponse(client, request, r))
                        .map(PublishResponse.PublishResponseBuilder::build)
                        .doOnNext(response -> {
                            if (log.isTraceEnabled() || properties.isLogResponse()) {
                                log.info("Received response : {}", response);
                            }
                        })
                        .timeout(properties.getTimeout())
                        .onErrorMap(t -> !(t instanceof PublishException), t -> new PublishException(request, t))
                        .doOnNext(scenario::onNext)
                        .doOnError(scenario::onError));
    }

    private Mono<TransactionResponse> getTransactionResponse(PublishRequest request, Client client) {
        Transaction<?> transaction = request.getTransaction();

        // set transaction node where applicable
        if (transaction.getNodeAccountIds() == null) {
            if (nodes.isEmpty()) {
                return Mono.error(new IllegalArgumentException("No valid nodes available"));
            }

            int nodeIndex = secureRandom.nextInt(nodes.size());
            var node = nodes.get(nodeIndex);
            transaction.setNodeAccountIds(node.getAccountIds());
        }

        return Mono.fromFuture(transaction.executeAsync(client));
    }

    private Mono<PublishResponse.PublishResponseBuilder> processTransactionResponse(Client client,
                                                                                    PublishRequest request,
                                                                                    TransactionResponse transactionResponse) {
        TransactionId transactionId = transactionResponse.transactionId;
        PublishResponse.PublishResponseBuilder builder = PublishResponse.builder()
                .request(request)
                .timestamp(Instant.now())
                .transactionId(transactionId);

        if (request.isRecord()) {
            // TransactionId.getRecordAsync() is inefficient doing a get receipt, a cost query, then the get record
            TransactionRecordQuery transactionRecordQuery = new TransactionRecordQuery()
                    .setQueryPayment(Hbar.from(1, HbarUnit.HBAR))
                    .setTransactionId(transactionId);
            return Mono.fromFuture(transactionRecordQuery.executeAsync(client))
                    .map(r -> builder.record(r).receipt(r.receipt));
        } else if (request.isReceipt()) {
            return Mono.fromFuture(transactionId.getReceiptAsync(client)).map(builder::receipt);
        }

        return Mono.just(builder);
    }

    private synchronized Flux<Client> getClients() {
        NodeValidationProperties validationProperties = monitorProperties.getNodeValidation();
        var configuredNodes = monitorProperties.getNodes();
        Map<String, AccountId> nodeMap = configuredNodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
        this.nodes.addAll(configuredNodes);

        Client client = toClient(nodeMap);
        client.setMaxAttempts(validationProperties.getMaxAttempts());
        client.setMaxBackoff(validationProperties.getMaxBackoff());
        client.setMinBackoff(validationProperties.getMinBackoff());
        client.setRequestTimeout(validationProperties.getRequestTimeout());
        this.validationClient.set(client);

        if (validationProperties.isEnabled() && nodeValidator.get() == null) {
            int nodeCount = configuredNodes.size();
            int parallelism = Math.min(nodeCount, validationProperties.getMaxThreads());

            var scheduler = Schedulers.newParallel("validator", parallelism + 1);
            var disposable = Flux.interval(Duration.ZERO, validationProperties.getFrequency(), scheduler)
                    .filter(i -> validationProperties.isEnabled()) // In case it's later disabled
                    .flatMap(i -> Flux.fromIterable(configuredNodes))
                    .parallel(parallelism)
                    .runOn(scheduler)
                    .map(this::validateNode)
                    .sequential()
                    .buffer(nodeCount)
                    .doOnNext(i -> log.info("{} of {} nodes are functional", nodes.size(), nodeCount))
                    .doOnSubscribe(s -> log.info("Starting node validation"))
                    .onErrorContinue((e, i) -> log.error("Exception validating nodes: ", e))
                    .subscribe();
            nodeValidator.set(disposable);
        }

        return Flux.range(0, publishProperties.getClients())
                .flatMap(i -> Mono.defer(() -> Mono.just(toClient(nodeMap))));
    }

    boolean validateNode(NodeProperties node) {
        try {
            log.info("Validating node {}", node);
            Hbar hbar = Hbar.fromTinybars(1L);
            AccountId nodeAccountId = AccountId.fromString(node.getAccountId());
            Client client = validationClient.get();

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

    private Client toClient(Map<String, AccountId> nodes) {
        AccountId operatorId = AccountId.fromString(monitorProperties.getOperator().getAccountId());
        PrivateKey operatorPrivateKey = PrivateKey.fromString(monitorProperties.getOperator().getPrivateKey());

        Client client = Client.forNetwork(nodes);
        client.setOperator(operatorId, operatorPrivateKey);
        return client;
    }
}
