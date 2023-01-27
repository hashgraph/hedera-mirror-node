package com.hedera.mirror.test.e2e.acceptance.client;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties.HederaNetwork.OTHER;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Value;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.util.CollectionUtils;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountCreateTransaction;
import com.hedera.hashgraph.sdk.AccountDeleteTransaction;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.mirror.test.e2e.acceptance.config.AcceptanceTestProperties;
import com.hedera.mirror.test.e2e.acceptance.props.ExpandedAccountId;
import com.hedera.mirror.test.e2e.acceptance.props.NodeProperties;

@CustomLog
@Named
@Value
public class SDKClient implements AutoCloseable {

    private final Client client;
    private final ExpandedAccountId defaultOperator;
    private final Map<String, AccountId> validateNetworkMap;
    private final AcceptanceTestProperties acceptanceTestProperties;
    private final MirrorNodeClient mirrorNodeClient;

    @Getter
    private final ExpandedAccountId expandedOperatorAccountId;

    public SDKClient(AcceptanceTestProperties acceptanceTestProperties, MirrorNodeClient mirrorNodeClient,
                     StartupProbe startupProbe)
            throws InterruptedException, TimeoutException {
        defaultOperator = new ExpandedAccountId(acceptanceTestProperties.getOperatorId(),
                acceptanceTestProperties.getOperatorKey());
        this.mirrorNodeClient = mirrorNodeClient;
        this.acceptanceTestProperties = acceptanceTestProperties;
        this.client = createClient();
        startupProbe.validateEnvironment(client);
        validateClient();
        expandedOperatorAccountId = getOperatorAccount();
        this.client.setOperator(expandedOperatorAccountId.getAccountId(), expandedOperatorAccountId.getPrivateKey());
        validateNetworkMap = this.client.getNetwork();
    }

    public AccountId getRandomNodeAccountId() {
        int randIndex = RandomUtils.nextInt(0, validateNetworkMap.size() - 1);
        return new ArrayList<>(validateNetworkMap.values()).get(randIndex);
    }

    @Override
    public void close() throws TimeoutException {
        var createdAccountId = expandedOperatorAccountId.getAccountId();
        var operatorId = defaultOperator.getAccountId();

        if (!operatorId.equals(createdAccountId)) {
            try {
                new AccountDeleteTransaction()
                        .setAccountId(createdAccountId)
                        .setGrpcDeadline(acceptanceTestProperties.getSdkProperties().getGrpcDeadline())
                        .setTransferAccountId(operatorId)
                        .execute(client);
                log.info("Deleted temporary operator account {}", createdAccountId);
            } catch (Exception e) {
                log.warn("Unable to delete temporary operator account {}", createdAccountId, e);
            }
        }

        client.close();
    }

    private Client createClient() throws InterruptedException {
        var customNodes = acceptanceTestProperties.getNodes();
        var network = acceptanceTestProperties.getNetwork();

        if (!CollectionUtils.isEmpty(customNodes)) {
            log.debug("Creating SDK client for {} network with nodes: {}", network, customNodes);
            return toClient(getNetworkMap(customNodes));
        }

        if (acceptanceTestProperties.isRetrieveAddressBook()) {
            try {
                return toClient(getAddressBook());
            } catch (Exception e) {
                log.warn("Error retrieving address book", e);
            }
        }

        if (network == OTHER && CollectionUtils.isEmpty(customNodes)) {
            throw new IllegalArgumentException("nodes must not be empty when network is OTHER");
        }

        return withDefaultOperator(Client.forName(network.toString().toLowerCase()));
    }

    private Map<String, AccountId> getNetworkMap(Set<NodeProperties> nodes) {
        return nodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
    }

    private ExpandedAccountId getOperatorAccount() {
        try {
            if (acceptanceTestProperties.isCreateOperatorAccount()) {
                PrivateKey privateKey = PrivateKey.generateED25519();
                PublicKey publicKey = privateKey.getPublicKey();
                var accountId = new AccountCreateTransaction()
                        .setInitialBalance(Hbar.fromTinybars(acceptanceTestProperties.getOperatorBalance()))
                        .setKey(publicKey)
                        .execute(client)
                        .getReceipt(client)
                        .accountId;
                log.info("Created operator account {} with public key {}", accountId, publicKey);
                return new ExpandedAccountId(accountId, privateKey, publicKey);
            }
        } catch (Exception e) {
            log.warn("Unable to create a regular operator account. Falling back to existing operator", e);
        }

        return defaultOperator;
    }

    private void validateClient() throws InterruptedException, TimeoutException {
        var network = client.getNetwork();
        Map<String, AccountId> validNodes = new LinkedHashMap<>();

        for (var nodeEntry : network.entrySet()) {
            var endpoint = nodeEntry.getKey();
            var nodeAccountId = nodeEntry.getValue();

            if (validateNode(endpoint, nodeAccountId)) {
                validNodes.putIfAbsent(endpoint, nodeAccountId);
                log.trace("Added node {} at endpoint {} to list of valid nodes", nodeAccountId, endpoint);
            }
        }

        log.info("Validated {} of {} nodes", validNodes.size(), network.size());
        if (validNodes.size() == 0) {
            throw new IllegalStateException("All provided nodes are unreachable!");
        }

        client.setNetwork(validNodes);
        log.info("Validated client with nodes: {}", validNodes);
    }

    private Client toClient(Map<String, AccountId> network) throws InterruptedException {
        return withDefaultOperator(Client.forNetwork(network))
                .setMirrorNetwork(List.of(acceptanceTestProperties.getMirrorNodeAddress()));
    }

    private boolean validateNode(String endpoint, AccountId nodeAccountId) {
        boolean valid = false;

        try (Client client = toClient(Map.of(endpoint, nodeAccountId))) {
            new AccountBalanceQuery()
                    .setAccountId(nodeAccountId)
                    .setGrpcDeadline(acceptanceTestProperties.getSdkProperties().getGrpcDeadline())
                    .setNodeAccountIds(List.of(nodeAccountId))
                    .execute(client, Duration.ofSeconds(10L));
            log.info("Validated node: {}", nodeAccountId);
            valid = true;
        } catch (Exception e) {
            log.warn("Unable to validate node {}: {}", nodeAccountId, e.getMessage());
        }

        return valid;
    }

    private Client withDefaultOperator(Client client) {
        var maxTransactionFee = Hbar.fromTinybars(acceptanceTestProperties.getMaxTinyBarTransactionFee());
        return client.setOperator(defaultOperator.getAccountId(), defaultOperator.getPrivateKey())
                .setDefaultMaxTransactionFee(maxTransactionFee);
    }

    private Map<String, AccountId> getAddressBook() {
        Map<String, AccountId> networkMap = new HashMap<>();
        var nodes = mirrorNodeClient.getNetworkNodes();

        for (var node : nodes) {
            var accountId = AccountId.fromString(node.getNodeAccountId());
            for (var serviceEndpoint : node.getServiceEndpoints()) {
                var ip = serviceEndpoint.getIpAddressV4();
                var port = serviceEndpoint.getPort();
                if (port == 50211) {
                    networkMap.putIfAbsent(ip + ":" + port, accountId);
                }
            }
        }

        log.info("Obtained address book with {} nodes and {} endpoints", nodes.size(), networkMap.size());
        return networkMap;
    }
}
