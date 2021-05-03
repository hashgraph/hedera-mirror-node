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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import lombok.Value;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.util.CollectionUtils;

import com.hedera.hashgraph.sdk.AccountBalanceQuery;
import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.FileContentsQuery;
import com.hedera.hashgraph.sdk.FileId;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.PrecheckStatusException;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.PublicKey;
import com.hedera.hashgraph.sdk.proto.NodeAddress;
import com.hedera.hashgraph.sdk.proto.NodeAddressBook;
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
    private final Hbar maxTransactionFee;
    private final Map<String, AccountId> validateNetworkMap;
    private static final FileId ADDRESS_BOOK_IPS = new FileId(0L, 0L, 101L);

    public SDKClient(AcceptanceTestProperties acceptanceTestProperties) throws InterruptedException,
            InvalidProtocolBufferException, PrecheckStatusException, TimeoutException {

        // Grab configuration variables from the .env file
        operatorId = AccountId.fromString(acceptanceTestProperties.getOperatorId());
        operatorKey = PrivateKey.fromString(acceptanceTestProperties.getOperatorKey());
        payerPublicKey = operatorKey.getPublicKey();
        mirrorNodeAddress = acceptanceTestProperties.getMirrorNodeAddress();
        messageTimeoutSeconds = acceptanceTestProperties.getMessageTimeout().toSeconds();
        maxTransactionFee = Hbar.fromTinybars(acceptanceTestProperties.getMaxTinyBarTransactionFee());

        Client client = getBootstrapClient(acceptanceTestProperties.getNetwork(), acceptanceTestProperties.getNodes());
        client.setOperator(operatorId, operatorKey);
        client.setMirrorNetwork(List.of(mirrorNodeAddress));

        Map<String, AccountId> networkMapToValidate = client.getNetwork();
        if (acceptanceTestProperties.isRetrieveAddressBook()) {
            try {
                networkMapToValidate = getAddressBookNetworkMap(client);
            } catch (Exception e) {
                //
            }
        }

        // only use validated nodes for tests
        this.client = getValidatedClient(networkMapToValidate, client);
        validateNetworkMap = this.client.getNetwork();
    }

    public ExpandedAccountId getExpandedOperatorAccountId() {
        return new ExpandedAccountId(operatorId, operatorKey, payerPublicKey);
    }

    public AccountId getRandomNodeAccountId() {
        int randIndex = RandomUtils.nextInt(0, validateNetworkMap.size() - 1);
        return new ArrayList<>(validateNetworkMap.values()).get(randIndex);
    }

    public void close() throws TimeoutException {
        client.close();
    }

    private Client getBootstrapClient(AcceptanceTestProperties.HederaNetwork network,
                                      Set<NodeProperties> customNodes) {
        if (!CollectionUtils.isEmpty(customNodes)) {
            log.debug("Creating SDK client for {} network with nodes: {}", network, customNodes);
            return Client.forNetwork(getNetworkMap(customNodes));
        }

        Client client;
        switch (network) {
            case MAINNET:
                log.debug("Creating SDK client for MainNet");
                client = Client.forMainnet();
                break;
            case PREVIEWNET:
                log.debug("Creating SDK client for PreviewNet");
                client = Client.forPreviewnet();
                break;
            case TESTNET:
                log.debug("Creating SDK client for TestNet");
                client = Client.forTestnet();
                break;
            default:
                throw new IllegalStateException("Unsupported network specified!");
        }

        return client;
    }

    private Map<String, AccountId> getNetworkMap(Set<NodeProperties> nodes) {
        return nodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
    }

    private Client getValidatedClient(Map<String, AccountId> currentNetworkMap, Client client) throws InterruptedException {
        Map<String, AccountId> validNodes = new LinkedHashMap<>();
        for (var nodeEntry : currentNetworkMap.entrySet()) {
            try {
                if (validateNode(nodeEntry.getValue().toString(), client)) {
                    validNodes.putIfAbsent(nodeEntry.getKey(), nodeEntry.getValue());
                    log.trace("Added node {} at endpoint {} to list of valid nodes", nodeEntry.getValue(),
                            nodeEntry.getKey());
                }
            } catch (Exception e) {
                //
            }
        }

        log.info("{} of {} nodes are reachable", validNodes.size(), currentNetworkMap.size());
        if (validNodes.size() == 0) {
            throw new IllegalStateException("All provided nodes are unreachable!");
        }

        log.info("Creating validated client using nodes: {} nodes", validNodes);
        Client validatedClient = Client.forNetwork(validNodes);
        validatedClient.setOperator(operatorId, operatorKey);
        validatedClient.setMirrorNetwork(List.of(mirrorNodeAddress));

        return validatedClient;
    }

    private boolean validateNode(String accountId, Client client) {
        boolean valid = false;
        try {
            AccountId nodeAccountId = AccountId.fromString(accountId);
            new AccountBalanceQuery()
                    .setAccountId(nodeAccountId)
                    .setNodeAccountIds(List.of(nodeAccountId))
                    .execute(client, Duration.ofSeconds(10L));
            log.trace("Validated node: {}", accountId);
            valid = true;
        } catch (Exception e) {
            log.warn("Unable to validate node {}: ", accountId, e);
        }

        return valid;
    }

    private NodeAddressBook getAddressBookFromNetwork(Client client) throws TimeoutException, PrecheckStatusException,
            InvalidProtocolBufferException {
        ByteString contents = new FileContentsQuery()
                .setFileId(ADDRESS_BOOK_IPS)
                .setMaxQueryPayment(new Hbar(1))
                .execute(client);

        log.debug("Retrieved address book with content size: {} b", contents.size());

        return NodeAddressBook.parseFrom(contents.toByteArray());
    }

    private Map<String, AccountId> getAddressBookNetworkMap(Client client) throws InvalidProtocolBufferException,
            PrecheckStatusException,
            TimeoutException {
        NodeAddressBook addressBook = getAddressBookFromNetwork(client);

        Map<String, AccountId> networkMap = new HashMap<>();
        for (NodeAddress nodeAddressProto : addressBook.getNodeAddressList()) {
            networkMap.putIfAbsent(
                    String.format("%s:%d", nodeAddressProto.getIpAddress().toStringUtf8(), nodeAddressProto
                            .getPortno()),
                    new AccountId(nodeAddressProto.getNodeAccountId().getShardNum(),
                            nodeAddressProto.getNodeAccountId().getRealmNum(),
                            nodeAddressProto.getNodeAccountId().getAccountNum()));
        }

        log.debug("Obtained addressBook networkMap: {}", networkMap);

        return networkMap;
    }
}
