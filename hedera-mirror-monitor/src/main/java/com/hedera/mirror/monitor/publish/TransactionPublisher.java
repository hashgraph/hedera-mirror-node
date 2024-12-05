/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.monitor.publish;

import com.hedera.hashgraph.sdk.AccountId;
import com.hedera.hashgraph.sdk.Client;
import com.hedera.hashgraph.sdk.Hbar;
import com.hedera.hashgraph.sdk.HbarUnit;
import com.hedera.hashgraph.sdk.PrivateKey;
import com.hedera.hashgraph.sdk.Query;
import com.hedera.hashgraph.sdk.Transaction;
import com.hedera.hashgraph.sdk.TransactionReceiptQuery;
import com.hedera.hashgraph.sdk.TransactionRecordQuery;
import com.hedera.hashgraph.sdk.TransactionResponse;
import com.hedera.hashgraph.sdk.proto.NodeAddressBook;
import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.NodeProperties;
import jakarta.inject.Named;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@Named
@RequiredArgsConstructor
public class TransactionPublisher implements AutoCloseable {

    private final MonitorProperties monitorProperties;
    private final NodeSupplier nodeSupplier;
    private final PublishProperties publishProperties;

    private final Flux<Client> clients = Flux.defer(this::getClients).cache();
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void close() {
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
                        .flatMap(r -> processTransactionResponse(client, request, r)))
                .map(PublishResponse.PublishResponseBuilder::build)
                .doOnNext(response -> {
                    if (log.isTraceEnabled() || properties.isLogResponse()) {
                        log.info("Received response : {}", response);
                    }
                })
                .timeout(properties.getTimeout())
                .onErrorMap(t -> !(t instanceof PublishException), t -> new PublishException(request, t))
                .doOnNext(scenario::onNext)
                .doOnError(scenario::onError);
    }

    private Mono<TransactionResponse> getTransactionResponse(PublishRequest request, Client client) {
        var transaction = request.getTransaction();

        if (request.getNode() == null) {
            var node = nodeSupplier.get();
            transaction.setNodeAccountIds(node.getAccountIds());
            request.setNode(node);
        }

        return execute(client, transaction);
    }

    private Mono<PublishResponse.PublishResponseBuilder> processTransactionResponse(
            Client client, PublishRequest request, TransactionResponse transactionResponse) {
        PublishResponse.PublishResponseBuilder builder = PublishResponse.builder()
                .request(request)
                .timestamp(Instant.now())
                .transactionId(transactionResponse.transactionId);

        if (request.isSendRecord()) {
            // TransactionId.getRecord() is inefficient doing a get receipt, a cost query, then the get record
            return execute(
                            client,
                            new TransactionRecordQuery()
                                    .setQueryPayment(Hbar.from(1, HbarUnit.HBAR))
                                    .setTransactionId(transactionResponse.transactionId))
                    .map(r -> builder.transactionRecord(r).receipt(r.receipt));
        } else if (request.isReceipt()) {
            return execute(client, new TransactionReceiptQuery().setTransactionId(transactionResponse.transactionId))
                    .map(builder::receipt);
        }

        return Mono.just(builder);
    }

    private Mono<TransactionResponse> execute(Client client, Transaction<?> executable) {
        if (publishProperties.isAsync()) {
            return Mono.fromFuture(executable.executeAsync(client));
        } else {
            return Mono.fromCallable(() -> executable.execute(client));
        }
    }

    private <T> Mono<T> execute(Client client, Query<T, ?> executable) {
        if (publishProperties.isAsync()) {
            return Mono.fromFuture(executable.executeAsync(client));
        } else {
            return Mono.fromCallable(() -> executable.execute(client));
        }
    }

    private Flux<Client> getClients() {
        return nodeSupplier.refresh().collect(Collectors.toList()).flatMapMany(nodes -> Flux.range(
                        0, publishProperties.getClients())
                .flatMap(i -> Mono.defer(() -> Mono.just(toClient(nodes)))));
    }

    @SneakyThrows
    private Client toClient(List<NodeProperties> nodes) {
        AccountId operatorId =
                AccountId.fromString(monitorProperties.getOperator().getAccountId());
        PrivateKey operatorPrivateKey =
                PrivateKey.fromString(monitorProperties.getOperator().getPrivateKey());

        // setNetworkFromAddressBook() doesn't support in-process URIs so we have to set network too
        var network = nodes.stream()
                .collect(Collectors.toMap(NodeProperties::getEndpoint, p -> AccountId.fromString(p.getAccountId())));
        var nodeAddresses = nodes.stream().map(NodeProperties::toNodeAddress).toList();
        var nodeAddressBook = NodeAddressBook.newBuilder()
                .addAllNodeAddress(nodeAddresses)
                .build()
                .toByteString();

        var client = Client.forNetwork(Map.of());
        client.setNetworkFromAddressBook(com.hedera.hashgraph.sdk.NodeAddressBook.fromBytes(nodeAddressBook));
        client.setNetwork(network);
        client.setNodeMaxBackoff(publishProperties.getNodeMaxBackoff());
        client.setOperator(operatorId, operatorPrivateKey);
        return client;
    }
}
