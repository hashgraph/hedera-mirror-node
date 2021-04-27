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

import com.google.common.base.Suppliers;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionId;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.NodeProperties;

@Log4j2
@Named
@RequiredArgsConstructor
public class TransactionPublisher {

    private final MonitorProperties monitorProperties;
    private final PublishProperties publishProperties;
    private final PublishMetrics publishMetrics;
    private final Supplier<List<Client>> clients = Suppliers.memoize(this::getClients);
    private final Supplier<List<AccountId>> nodeAccountIds = Suppliers.memoize(this::getNodeAccountIds);
    private final AtomicInteger counter = new AtomicInteger(0);
    private final SecureRandom secureRandom = new SecureRandom();

    @PreDestroy
    public void close() {
        log.info("Closing {} clients", clients.get().size());

        for (Client client : clients.get()) {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public Mono<PublishResponse> publish(PublishRequest request) {
        return doPublish(request)
                .doOnSuccess(response -> publishMetrics.onSuccess(request, response))
                .doOnError(throwable -> publishMetrics.onError(request, throwable))
                .onErrorMap(PublishException::new);
    }

    private Mono<PublishResponse> doPublish(PublishRequest request) {
        log.trace("Publishing: {}", request);
        int clientIndex = counter.getAndUpdate(n -> (n + 1 < clients.get().size()) ? n + 1 : 0);
        Client client = clients.get().get(clientIndex);

        return getTransactionResponse(request, client)
                .flatMap(transactionResponse -> processTransactionResponse(client, request, transactionResponse))
                .map(PublishResponse.PublishResponseBuilder::build)
                .doOnNext(response -> {
                    if (log.isTraceEnabled() || request.isLogResponse()) {
                        log.info("Received response : {}", response);
                    }
                })
                .timeout(request.getTimeout());
    }

    private Mono<TransactionResponse> getTransactionResponse(PublishRequest request, Client client) {
        Transaction<?> transaction = request.getTransaction();

        // set transaction node where applicable
        if (transaction.getNodeAccountIds() == null) {
            int nodeIndex = secureRandom.nextInt(nodeAccountIds.get().size());
            List<AccountId> nodeAccountId = List.of(nodeAccountIds.get().get(nodeIndex));
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
            return Mono.fromFuture(transactionId.getRecordAsync(client))
                    .map(r -> builder.record(r).receipt(r.receipt));
        } else if (request.isReceipt()) {
            // TODO: Implement a faster retry for get receipt for more accurate metrics
            return Mono.fromFuture(transactionId.getReceiptAsync(client))
                    .map(builder::receipt);
        }

        return Mono.just(builder);
    }

    private List<Client> getClients() {
        Collection<NodeProperties> validNodes = validateNodes();

        if (validNodes.isEmpty()) {
            throw new IllegalArgumentException("No valid nodes found");
        }

        List<Client> validatedClients = new ArrayList<>();
        Map<String, AccountId> network = toNetwork(validNodes);
        for (int i = 0; i < publishProperties.getClients(); ++i) {
            Client client = toClient(network);
            validatedClients.add(client);
        }

        return validatedClients;
    }

    private List<AccountId> getNodeAccountIds() {
        return new ArrayList<>(clients.get().get(0).getNetwork().values());
    }

    private Collection<NodeProperties> validateNodes() {
        Set<NodeProperties> nodes = monitorProperties.getNodes();

        if (!monitorProperties.isValidateNodes()) {
            return nodes;
        }

        List<NodeProperties> validNodes = new ArrayList<>();
        try (Client client = toClient(toNetwork(nodes))) {
            for (NodeProperties node : nodes) {
                if (validateNode(client, node)) {
                    validNodes.add(node);
                }
            }
        } catch (Exception e) {
            //
        }

        log.info("{} of {} nodes are functional", validNodes.size(), nodes.size());
        return validNodes;
    }

    private boolean validateNode(Client client, NodeProperties node) {
        boolean valid = false;
        try {
            AccountId nodeAccountId = AccountId.fromString(node.getAccountId());
            new AccountBalanceQuery()
                    .setAccountId(nodeAccountId)
                    .setNodeAccountIds(List.of(nodeAccountId))
                    .execute(client, Duration.ofSeconds(10L));
            log.info("Validated node: {}", node);
            valid = true;
        } catch (Exception e) {
            log.warn("Unable to validate node {}: ", node, e);
        }

        return valid;
    }

    private Client toClient(Map<String, AccountId> network) {
        AccountId operatorId = AccountId.fromString(monitorProperties.getOperator().getAccountId());
        PrivateKey operatorPrivateKey = PrivateKey.fromString(monitorProperties.getOperator().getPrivateKey());
        Client client = Client.forNetwork(network);
        client.setOperator(operatorId, operatorPrivateKey);
        return client;
    }

    private Map<String, AccountId> toNetwork(Collection<NodeProperties> nodes) {
        return nodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
    }
}
