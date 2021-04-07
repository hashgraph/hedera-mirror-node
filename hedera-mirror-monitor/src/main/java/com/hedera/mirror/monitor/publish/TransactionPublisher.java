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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountInfoQuery;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PrivateKey;
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

    public CompletableFuture<PublishResponse> publish(PublishRequest request) {
        return publishMetrics.record(request, this::doPublish);
    }

    private CompletableFuture<PublishResponse> doPublish(PublishRequest request) {
        log.trace("Publishing: {}", request);
        int clientIndex = counter.getAndUpdate(n -> (n + 1 < clients.get().size()) ? n + 1 : 0);
        Client client = clients.get().get(clientIndex);
        int nodeIndex = secureRandom.nextInt(nodeAccountIds.get().size());
        request.getTransaction().setNodeAccountIds(List.of(nodeAccountIds.get().get(nodeIndex)));

        CompletableFuture<PublishResponse> publishResponse = request.getTransaction()
                .executeAsync(client)
                .thenCompose(transactionResponse -> processTransactionResponse(client, request, transactionResponse))
                .thenApply(PublishResponse.PublishResponseBuilder::build)
                .thenApply(response -> {
                    if (log.isTraceEnabled() || request.isLogResponse()) {
                        log.info("Received response : {}", response);
                    }

                    return response;
                });

        return request.getTimeout() != null ? publishResponse
                .orTimeout(request.getTimeout().toMillis(), TimeUnit.MILLISECONDS) : publishResponse;
    }

    private CompletableFuture<PublishResponse.PublishResponseBuilder> processTransactionResponse(Client client,
            PublishRequest request, TransactionResponse transactionResponse) {
        TransactionId transactionId = transactionResponse.transactionId;
        PublishResponse.PublishResponseBuilder builder = PublishResponse.builder()
                .request(request)
                .timestamp(Instant.now())
                .transactionId(transactionId);

        if (request.isRecord()) {
            return transactionId.getRecordAsync(client)
                    .thenApply(record -> builder.record(record).receipt(record.receipt));
        } else if (request.isReceipt()) {
            // TODO: Implement a faster retry for get receipt for more accurate metrics
            return transactionId.getReceiptAsync(client).thenApply(builder::receipt);
        }

        return CompletableFuture.completedFuture(builder);
    }

    private List<Client> getClients() {
        List<NodeProperties> validNodes = validateNodes();

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

    private List<NodeProperties> validateNodes() {
        List<NodeProperties> nodes = new ArrayList<>(new HashSet<>(monitorProperties.getNodes()));

        if (!monitorProperties.isValidateNodes()) {
            return nodes;
        }

        List<NodeProperties> validNodes = new ArrayList<>();
        AccountId accountId = AccountId.fromString("0.0.2");

        try (Client client = toClient(toNetwork(nodes))) {
            for (NodeProperties node : nodes) {
                if (validateNode(accountId, client, node)) {
                    validNodes.add(node);
                }
            }
        } catch (Exception e) {
            //
        }

        log.info("{} of {} nodes are functional", validNodes.size(), nodes.size());
        return validNodes;
    }

    private boolean validateNode(AccountId accountId, Client client, NodeProperties node) {
        boolean valid = false;
        try {
            new AccountInfoQuery()
                    .setAccountId(accountId)
                    .setNodeAccountIds(List.of(AccountId.fromString(node.getAccountId())))
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

    private Map<String, AccountId> toNetwork(List<NodeProperties> nodes) {
        return nodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
    }
}
