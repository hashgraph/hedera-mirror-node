package com.hedera.mirror.monitor.publish;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.account.AccountId;
import com.hedera.hashgraph.sdk.account.AccountInfoQuery;
import com.hedera.hashgraph.sdk.crypto.PrivateKey;
import com.hedera.hashgraph.sdk.crypto.ed25519.Ed25519PrivateKey;

@Log4j2
@Named
public class TransactionPublisher {

    private final PublishProperties properties;
    private final PublisherMetrics publisherMetrics;
    private final List<Client> clients;

    public TransactionPublisher(PublishProperties properties, PublisherMetrics publisherMetrics) {
        this.properties = properties;
        this.publisherMetrics = publisherMetrics;
        clients = new ArrayList<>();
        List<NodeProperties> validNodes = validateNodes(properties);
        Random random = new Random();

        for (int i = 0; i < properties.getConnections(); ++i) {
            NodeProperties nodeProperties = validNodes.get(random.nextInt(validNodes.size()));
            Client client = toClient(nodeProperties);
            clients.add(client);
        }
    }

    public PublishResponse publish(PublishRequest request) {
        return publisherMetrics.record(() -> doPublish(request));
    }

    private PublishResponse doPublish(PublishRequest request) throws Exception {
        log.trace("Publishing: {}", request);
        int index = ThreadLocalRandom.current().nextInt(clients.size());
        Client client = clients.get(index);

        var transactionId = request.getTransactionBuilder().execute(client);
        PublishResponse.PublishResponseBuilder responseBuilder = PublishResponse.builder().transactionId(transactionId);

        if (request.isRecord()) {
            var record = transactionId.getRecord(client);
            responseBuilder.record(record).receipt(record.receipt);
        } else if (request.isReceipt()) {
            var receipt = transactionId.getReceipt(client);
            responseBuilder.receipt(receipt);
        }

        PublishResponse response = responseBuilder.build();
        log.trace("Received response: {}", response);
        return response;
    }

    private List<NodeProperties> validateNodes(PublishProperties properties) {
        Set<NodeProperties> nodes = properties.getNodes();

        if (!properties.isValidateNodes()) {
            return new ArrayList<>(nodes);
        }

        List<NodeProperties> validNodes = new ArrayList<>();
        AccountId accountId = AccountId.fromString("0.0.2");

        for (NodeProperties node : nodes) {
            Client client = toClient(node);

            try {
                new AccountInfoQuery().setAccountId(accountId).execute(client, Duration.ofSeconds(10L));
                validNodes.add(node);
                log.info("Added node {} to the list of valid nodes", node);
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
        AccountId operatorId = AccountId.fromString(properties.getOperator().getAccountId());
        PrivateKey<?> operatorPrivateKey = Ed25519PrivateKey.fromString(properties.getOperator().getPrivateKey());

        Client client = new Client(Map.of(nodeAccount, nodeProperties.getEndpoint()));
        client.setOperator(operatorId, operatorPrivateKey);
        return client;
    }
}
