package com.hedera.mirror.test.e2e.acceptance.client;

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

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.log4j.Log4j2;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.NodeProperties;

@Log4j2
@Value
public class SDKClient {
    private final Client client;
    private final PublicKey payerPublicKey;
    private final PrivateKey operatorKey;
    private final AccountId operatorId;
    private final String mirrorNodeAddress;
    private final long messageTimeoutSeconds;
    private final List<AccountId> singletonNodeId;
    private final Hbar maxTransactionFee;

    public SDKClient(AcceptanceTestProperties acceptanceTestProperties) throws InterruptedException {

        // Grab configuration variables from the .env file
        operatorId = AccountId.fromString(acceptanceTestProperties.getOperatorId());
        operatorKey = PrivateKey.fromString(acceptanceTestProperties.getOperatorKey());
        payerPublicKey = operatorKey.getPublicKey();
        mirrorNodeAddress = acceptanceTestProperties.getMirrorNodeAddress();
        messageTimeoutSeconds = acceptanceTestProperties.getMessageTimeout().toSeconds();
        maxTransactionFee = Hbar.fromTinybars(acceptanceTestProperties.getMaxTinyBarTransactionFee());

        Client client;
        var network = acceptanceTestProperties.getNetwork();
        switch (network) {
            case TESTNET:
                log.debug("Creating SDK client for TestNet");
                client = Client.forTestnet();
                break;
            case MAINNET:
                log.debug("Creating SDK client for MainNet");
                client = Client.forMainnet();
                break;
            default:
//                Set<NodeProperties> validNodes = validateNodes(acceptanceTestProperties);
//                log.debug("Creating SDK client using {} valid nodes", validNodes.size());
//                client = toClient(getNetworkMap(validNodes));
                log.debug("Creating SDK client for OTHER");
                client = toClient(getNetworkMap(acceptanceTestProperties.getNodes()));
        }

//        Set<NodeProperties> validNodes = validateNodes(client.getNetwork());
//        log.debug("Creating SDK client using {} valid nodes", validNodes.size());
//        client = toClient(getNetworkMap(validNodes));

//        client.setOperator(operatorId, operatorKey);
//        client.setMirrorNetwork(List.of(acceptanceTestProperties.getMirrorNodeAddress()));

        // if prod environment, get current address book and use those nodes

        // only use validated nodes for tests
        this.client = getValidatedClient(client.getNetwork());

        // set nodeId to first valid node
        singletonNodeId = Collections.singletonList(this.client.getNetwork().values().iterator().next());
    }

    public ExpandedAccountId getExpandedOperatorAccountId() {
        return new ExpandedAccountId(operatorId, operatorKey, payerPublicKey);
    }

    public void close() throws TimeoutException {
        client.close();
    }

    private Client toClient(Map<String, AccountId> network) throws InterruptedException {
        Client client = Client.forNetwork(network);
        client.setOperator(operatorId, operatorKey);
        client.setMirrorNetwork(List.of(mirrorNodeAddress));
        return client;
    }

    private Map<String, AccountId> getNetworkMap(Set<NodeProperties> nodes) {
        return nodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
    }

    private Set<NodeProperties> validateNodes(AcceptanceTestProperties acceptanceTestProperties) {
        Set<NodeProperties> nodes = acceptanceTestProperties.getNodes();
        Set<NodeProperties> validNodes = new HashSet<>();
        try {
            for (NodeProperties node : nodes) {
                if (validateNode(node)) {
                    validNodes.add(node);
                    log.trace("Added node {} at endpoint {} to list of valid nodes", node.getAccountId(), node
                            .getEndpoint());
                }
            }
        } catch (Exception e) {
            //
        }

        if (validNodes.size() == 0) {
            throw new IllegalStateException("All provided nodes are unreachable!");
        }

        log.info("{} of {} nodes are functional", validNodes.size(), nodes.size());
        return validNodes;
    }

    private Client getValidatedClient(Map<String, AccountId> currentNetworkMap) throws InterruptedException {
        Map<String, AccountId> validNodes = new HashMap<>();
//        AccountId firstValidNodeId = null;
        for (var nodeEntry : currentNetworkMap.entrySet()) {
            try {
                NodeProperties node = new NodeProperties(nodeEntry.getValue().toString(), nodeEntry.getKey());
                if (validateNode(node)) {
                    validNodes.putIfAbsent(nodeEntry.getKey(), nodeEntry.getValue());
                    log.trace("Added node {} at endpoint {} to list of valid nodes", node.getAccountId(), node
                            .getHost());

                    // set nodeId to first valid node
//                    if (singletonNodeId == null) {
//                        firstValidNodeId = nodeEntry.getValue();
//                        singletonNodeId = Collections.singletonList(nodeEntry.getValue());
//                    }
                }
            } catch (Exception e) {
                //
            }
        }

//        if (firstValidNodeId != null) {
//            singletonNodeId = Collections.singletonList(firstValidNodeId);
//        }

        if (validNodes.size() == 0) {
            throw new IllegalStateException("All provided nodes are unreachable!");
        }

        log.info("{} of {} nodes are functional", validNodes.size(), currentNetworkMap.size());
        return toClient(validNodes);
    }

    private boolean validateNode(NodeProperties node) {
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

    private Client getNodesFromCurrentAddressBook() {
        return null;
    }
}
