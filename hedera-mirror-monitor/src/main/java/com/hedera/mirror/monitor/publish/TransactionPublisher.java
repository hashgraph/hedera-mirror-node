package com.hedera.mirror.monitor.publish;

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

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    protected final AtomicReference<List<AccountId>> nodeAccountIds = new AtomicReference<>(List.of());
    private final Flux<Client> clients = Flux.defer(this::getClients).cache();
    private final SecureRandom secureRandom = new SecureRandom();
    private Optional<Disposable> nodeValidator = Optional.empty();

    @Override
    public void close() {
        nodeValidator.ifPresent(Disposable::dispose);

        if (publishProperties.isEnabled()) {
            log.warn("Closing {} clients", publishProperties.getClients());
            clients.subscribe(client -> {
                try {
                    client.close();
                } catch (Exception e) {
                    // Ignore
                }
            });
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
                        .onErrorMap(t -> new PublishException(request, t))
                        .doOnNext(scenario::onNext)
                        .doOnError(scenario::onError));
    }

    private Mono<TransactionResponse> getTransactionResponse(PublishRequest request, Client client) {
        Transaction<?> transaction = request.getTransaction();
        transaction.setTransactionMemo(request.getScenario().getMemo());

        // set transaction node where applicable
        if (transaction.getNodeAccountIds() == null) {
            List<AccountId> nodes = nodeAccountIds.get();
            if (nodes.isEmpty()) {
                throw new PublishException(request, new IllegalArgumentException("No valid nodes available to publish" +
                        " to"));
            }
            int nodeIndex = secureRandom.nextInt(nodes.size());
            List<AccountId> nodeAccountId = List.of(nodes.get(nodeIndex));
            transaction.setNodeAccountIds(nodeAccountId);
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

    private Flux<Client> getClients() {
        validateNodes();

        log.info("Creating {} connections to {} nodes", publishProperties.getClients(), monitorProperties.getNodes()
                .size());
        Map<String, AccountId> nodes = monitorProperties.getNodes().stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));

        NodeValidationProperties validationProperties = monitorProperties.getNodeValidation();
        if (validationProperties.isEnabled()) {

            nodeValidator = Optional.of(Flux.interval(validationProperties.getFrequency(),
                            validationProperties.getFrequency())
                    .subscribeOn(Schedulers.parallel())
                    .doOnNext(i -> validateNodes())
                    .onErrorContinue((e, i) -> log.error("Exception revalidating nodes: {}", e))
                    .subscribe());
        }
        return Flux.range(0, publishProperties.getClients())
                .flatMap(i -> Flux.defer(() -> Mono.just(toClient(nodes))));
    }

    private void validateNodes() {
        log.info("Validating nodes");
        Set<NodeProperties> nodes = monitorProperties.getNodes();

        if (!monitorProperties.getNodeValidation().isEnabled()) {
            setNodeAccountIds(new ArrayList<>(nodes));
            return;
        }

        List<NodeProperties> validNodes = new ArrayList<>();

        for (NodeProperties node : nodes) {
            AccountId nodeAccountId = AccountId.fromString(node.getAccountId());

            try (Client client = toClient(Map.of(node.getEndpoint(), nodeAccountId))) {
                if (validateNode(client, node)) {
                    validNodes.add(node);
                }
            } catch (Exception e) {
                log.warn("Error validating nodes: {}", e.getMessage());
            }
        }

        log.info("{} of {} nodes are functional", validNodes.size(), nodes.size());

        setNodeAccountIds(validNodes);

        if (nodeAccountIds.get().isEmpty()) {
            throw new IllegalArgumentException("No valid nodes found");
        }
    }

    private void setNodeAccountIds(List<NodeProperties> validNodes) {
        List<AccountId> newNodeAccountIds = new ArrayList<>();
        validNodes.forEach(n -> newNodeAccountIds.add(AccountId.fromString(n.getAccountId())));
        nodeAccountIds.set(newNodeAccountIds);
    }

    private boolean validateNode(Client client, NodeProperties node) {
        boolean valid = false;
        NodeValidationProperties nodeValidationProperties = monitorProperties.getNodeValidation();
        client.setMinBackoff(nodeValidationProperties.getMinBackoff());
        client.setMaxBackoff(nodeValidationProperties.getMaxBackoff());
        client.setMaxAttempts(nodeValidationProperties.getMaxAttempts());
        try {
            AccountId nodeAccountId = AccountId.fromString(node.getAccountId());
            Hbar hbar = Hbar.fromTinybars(1L);
            new TransferTransaction()
                    .addHbarTransfer(nodeAccountId, hbar)
                    .addHbarTransfer(client.getOperatorAccountId(), hbar.negated())
                    .execute(client, Duration.ofSeconds(30L))
                    .getReceipt(client, Duration.ofSeconds(30L));
            log.info("Validated node: {}", node);
            valid = true;
        } catch (TimeoutException e) {
            log.warn("Unable to validate node {}: Timed out", node);
        } catch (Exception e) {
            log.warn("Unable to validate node {}: ", node, e);
        }

        return valid;
    }

    private Client toClient(Map<String, AccountId> nodes) {
        AccountId operatorId = AccountId.fromString(monitorProperties.getOperator().getAccountId());
        PrivateKey operatorPrivateKey = PrivateKey.fromString(monitorProperties.getOperator().getPrivateKey());

        Client client = Client.forNetwork(nodes);
        client.setOperator(operatorId, operatorPrivateKey);
        return client;
    }
}
