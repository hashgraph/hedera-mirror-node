package com.hedera.mirror.test.e2e.acceptance.client;

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
import lombok.Getter;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
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

@Log4j2
@Named
@Value
public class SDKClient implements AutoCloseable {

    private final Client client;
    private final Hbar maxTransactionFee;
    private final Map<String, AccountId> validateNetworkMap;
    private final AcceptanceTestProperties acceptanceTestProperties;
    private final MirrorNodeClient mirrorNodeClient;
    private final StartupProbe startupProbe;

    @Getter
    private final ExpandedAccountId expandedOperatorAccountId;

    public SDKClient(AcceptanceTestProperties acceptanceTestProperties, MirrorNodeClient mirrorNodeClient,
            StartupProbe startupProbe)
            throws InterruptedException, TimeoutException {
        this.mirrorNodeClient = mirrorNodeClient;
        maxTransactionFee = Hbar.fromTinybars(acceptanceTestProperties.getMaxTinyBarTransactionFee());
        this.acceptanceTestProperties = acceptanceTestProperties;
        this.startupProbe = startupProbe;

        Map<String, AccountId> network = getNetworkMap();
        // Client in next line is only used by the Startup probe.  Validated Client for SDKClient set afterwards.
        try (Client unvalidatedClient = toClient(network)) {
            startupProbe.validateEnvironment(unvalidatedClient);
        } catch (Exception e) {
            log.warn("Startup Probe Failed: {}", e.getMessage());
        } 
        this.client = getValidatedClient();
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
        var operatorId = AccountId.fromString(acceptanceTestProperties.getOperatorId());

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

    private Map<String, AccountId> getNetworkMap() {
        var customNodes = acceptanceTestProperties.getNodes();
        var network = acceptanceTestProperties.getNetwork();

        if (!CollectionUtils.isEmpty(customNodes)) {
            log.debug("Creating SDK client for {} network with nodes: {}", network, customNodes);
            return getNetworkMap(customNodes);
        }

        if (acceptanceTestProperties.isRetrieveAddressBook()) {
            try {
                return getAddressBook();
            } catch (Exception e) {
                log.warn("Error retrieving address book", e);
            }
        }

        if (network == OTHER && CollectionUtils.isEmpty(customNodes)) {
            throw new IllegalArgumentException("nodes must not be empty when network is OTHER");
        }

        Client client = Client.forName(network.toString().toLowerCase());
        return client.getNetwork();
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
                        .setMaxTransactionFee(getMaxTransactionFee())
                        .execute(client)
                        .getReceipt(client)
                        .accountId;
                log.info("Created operator account {} with public key {}", accountId, publicKey);
                return new ExpandedAccountId(accountId, privateKey, publicKey);
            }
        } catch (Exception e) {
            log.warn("Unable to create a regular operator account. Falling back to existing operator");
        }

        var operatorId = acceptanceTestProperties.getOperatorId();
        var operatorKey = acceptanceTestProperties.getOperatorKey();
        return new ExpandedAccountId(operatorId, operatorKey);
    }

    private Client getValidatedClient() throws InterruptedException {
        Map<String, AccountId> network = getNetworkMap();
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

        log.info("Creating validated client using nodes: {}", validNodes);
        return toClient(validNodes);
    }

    private Client toClient(Map<String, AccountId> network) throws InterruptedException {
        var operatorId = AccountId.fromString(acceptanceTestProperties.getOperatorId());
        var operatorKey = PrivateKey.fromString(acceptanceTestProperties.getOperatorKey());
        Client client = Client.forNetwork(network);
        client.setOperator(operatorId, operatorKey);
        client.setMirrorNetwork(List.of(acceptanceTestProperties.getMirrorNodeAddress()));
        return client;
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
