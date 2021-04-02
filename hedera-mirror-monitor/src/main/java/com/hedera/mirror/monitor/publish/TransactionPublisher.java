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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.TransactionId;
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
    private final AtomicInteger counter = new AtomicInteger(0);
    private final SecureRandom secureRandom = new SecureRandom();

    @PreDestroy
    public void close() {
        log.info("Closing {} client connections", clients.get().size());

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
        int index = counter.getAndUpdate(n -> (n + 1 < clients.get().size()) ? n + 1 : 0);

        Client client = clients.get().get(index);
        List<AccountId> nodeAccountIds = new ArrayList<>(client.getNetwork().values());
        int nodeIndex = secureRandom.nextInt(client.getNetwork().size());
        request.getTransaction().setNodeAccountIds(List.of(nodeAccountIds.get(nodeIndex)));

        return request.getTransaction()
                .executeAsync(client)
                .thenCompose(transactionResponse -> {
                    TransactionId transactionId = transactionResponse.transactionId;
                    PublishResponse.PublishResponseBuilder builder = PublishResponse.builder()
                            .request(request)
                            .timestamp(Instant.now())
                            .transactionId(transactionId);

                    if (request.isRecord()) {
                        return transactionId.getRecordAsync(client)
                                .thenApply(record -> builder.record(record).receipt(record.receipt));
                    } else if (request.isReceipt()) {
                        return transactionId.getReceiptAsync(client).thenApply(builder::receipt);
                    }

                    return CompletableFuture.completedFuture(builder);
                })
                .thenApply(builder -> {
                    PublishResponse response = builder.build();

                    if (log.isTraceEnabled() || request.isLogResponse()) {
                        log.info("Received response : {}", response);
                    }

                    return response;
                });
    }

    private List<Client> getClients() {
        List<NodeProperties> validNodes = validateNodes();

        if (validNodes.isEmpty()) {
            throw new IllegalArgumentException("No valid nodes found");
        }

        List<Client> validatedClients = new ArrayList<>();

        log.info("Creating {} clients", publishProperties.getClients());

//        for (int i = 0; i < publishProperties.getClients(); ++i) {
//            NodeProperties nodeProperties = validNodes.get(i % validNodes.size());
            Client client = toClient(validNodes);
            validatedClients.add(client);
//        }

        return validatedClients;
    }

    private List<NodeProperties> validateNodes() {
        Set<NodeProperties> nodes = monitorProperties.getNodes();

        if (!monitorProperties.isValidateNodes()) {
            return new ArrayList<>(nodes);
        }

        List<NodeProperties> validNodes = new ArrayList<>(nodes);
//        AccountId accountId = AccountId.fromString("0.0.2");

//        for (NodeProperties node : nodes) {
//            Client client = toClient(node);
//
//            try {
//                new AccountInfoQuery().setAccountId(accountId).execute(client, Duration.ofSeconds(10L));
//                validNodes.add(node);
//                log.info("Validated node: {}", node);
//            } catch (Exception e) {
//                log.warn("Unable to validate node {}: ", node, e);
//            } finally {
//                try {
//                    client.close();
//                } catch (Exception e) {
//                }
//            }
//        }

        log.info("{} of {} nodes are functional", validNodes.size(), nodes.size());
        return validNodes;
    }

    private Client toClient(List<NodeProperties> validNodes) {
        Map<String, AccountId> network = validNodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
        AccountId operatorId = AccountId.fromString(monitorProperties.getOperator().getAccountId());
        PrivateKey operatorPrivateKey = PrivateKey
                .fromString(monitorProperties.getOperator().getPrivateKey());

        Client client = Client.forNetwork(network);
        client.setOperator(operatorId, operatorPrivateKey);
        return client;
    }
}
