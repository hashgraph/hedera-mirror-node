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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.AccountInfoQuery;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.ReceiptStatusException;
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

    public PublishResponse publish(PublishRequest request) {
        return publishMetrics.record(request, this::doPublish);
    }

    private PublishResponse doPublish(PublishRequest request) throws TimeoutException, PrecheckStatusException,
            ReceiptStatusException {
        log.trace("Publishing: {}", request);
        int index = counter.getAndUpdate(n -> (n + 1 < clients.get().size()) ? n + 1 : 0);
        Client client = clients.get().get(index);

        TransactionId transactionId = request.getTransactionBuilder().execute(client).transactionId;
        PublishResponse.PublishResponseBuilder responseBuilder = PublishResponse.builder()
                .request(request)
                .timestamp(Instant.now())
                .transactionId(transactionId);

        if (request.isRecord()) {
            var record = transactionId.getRecord(client);
            responseBuilder.record(record).receipt(record.receipt);
        } else if (request.isReceipt()) {
            // TODO: Implement a faster retry for get receipt for more accurate metrics
            var receipt = transactionId.getReceipt(client);
            responseBuilder.receipt(receipt);
        }

        PublishResponse response = responseBuilder.build();

        if (log.isTraceEnabled() || request.isLogResponse()) {
            log.info("Received response: {}", response);
        }

        return response;
    }

    private List<Client> getClients() {
        List<NodeProperties> validNodes = validateNodes();

        if (validNodes.isEmpty()) {
            throw new IllegalArgumentException("No valid nodes found");
        }

        List<Client> validatedClients = new ArrayList<>();

        for (int i = 0; i < publishProperties.getConnections(); ++i) {
            NodeProperties nodeProperties = validNodes.get(i % validNodes.size());
            Client client = toClient(nodeProperties);
            validatedClients.add(client);
        }

        return validatedClients;
    }

    private List<NodeProperties> validateNodes() {
        Set<NodeProperties> nodes = monitorProperties.getNodes();

        if (!monitorProperties.isValidateNodes()) {
            return new ArrayList<>(nodes);
        }

        List<NodeProperties> validNodes = new ArrayList<>();
        AccountId accountId = AccountId.fromString("0.0.2");

        for (NodeProperties node : nodes) {
            Client client = toClient(node);

            try {
                new AccountInfoQuery().setAccountId(accountId).execute(client, Duration.ofSeconds(10L));
                validNodes.add(node);
                log.info("Validated node: {}", node);
            } catch (Exception e) {
                log.warn("Unable to validate node {}: ", node, e);
            } finally {
                try {
                    client.close();
                } catch (Exception e) {
                }
            }
        }

        log.info("{} of {} nodes are functional", validNodes.size(), nodes.size());
        return validNodes;
    }

    private Client toClient(NodeProperties nodeProperties) {
        AccountId nodeAccount = AccountId.fromString(nodeProperties.getAccountId());
        AccountId operatorId = AccountId.fromString(monitorProperties.getOperator().getAccountId());
        PrivateKey operatorPrivateKey = PrivateKey
                .fromString(monitorProperties.getOperator().getPrivateKey());

        Client client = Client.forNetwork(Map.of(nodeProperties.getEndpoint(), nodeAccount));
        client.setOperator(operatorId, operatorPrivateKey);
        return client;
    }
}
